package ru.spbstu.queueservice.service

import jakarta.annotation.PreDestroy
import mu.KotlinLogging
import org.springframework.stereotype.Service
import ru.spbstu.queueservice.client.ChefServiceClient
import ru.spbstu.queueservice.client.OrderServiceClient
import ru.spbstu.queueservice.config.QueueConfig
import ru.spbstu.queueservice.model.Order
import ru.spbstu.queueservice.model.QueueResponse
import java.util.*
import kotlin.math.sqrt

@Service
class QueueService(
    private val chefServiceClient: ChefServiceClient,
    private val orderServiceClient: OrderServiceClient,
    private val queueConfig: QueueConfig
) {
    private val logger = KotlinLogging.logger {}
    private val queue: MutableList<Order> = mutableListOf()
    private var currentPackage: MutableList<Order>? = null
    private var availableChefs = 0

    private val lock = Any()

    private val orderStartTimes: MutableMap<UUID, Long> = mutableMapOf()
    private val orderEndsTimes: MutableMap<UUID, Long> = mutableMapOf()
    private val waitingOrderCount: MutableMap<Long, Int> = mutableMapOf()

    private val queueWaitingTimesPerSource: MutableMap<Long, MutableList<Long>> = mutableMapOf()

    fun addOrder(order: Order): QueueResponse {
        return synchronized(lock) {
            val response = addToQueue(order)
            if (response == QueueResponse.ACCEPTED) {
                processNextOrderForChef()
            }
            response
        }
    }

    private fun addToQueue(order: Order): QueueResponse {
        val startTime = System.currentTimeMillis()
        if (queue.size < queueConfig.size) {
            queue.add(order)
            orderStartTimes[order.id] = startTime
            logger.info("Added order ${order.id} from customer ${order.customerId}. Space left: ${queueConfig.size - queue.size}")
            waitingOrderCount[order.customerId] = waitingOrderCount[order.customerId]?.inc() ?: 1
            return QueueResponse.ACCEPTED
        }

        val lowestPriorityOrder = queue.minWithOrNull(compareBy<Order> { it.customerId }.thenBy { it.addingTime })
        if (lowestPriorityOrder != null && lowestPriorityOrder.customerId < order.customerId) {
            queue.remove(lowestPriorityOrder)
            val endTime = System.currentTimeMillis()
            orderEndsTimes[lowestPriorityOrder.id] = endTime
            val startTimeRemoved = orderStartTimes.remove(lowestPriorityOrder.id)
            if (startTimeRemoved != null) {
                val waitingTime = endTime - startTimeRemoved
                queueWaitingTimesPerSource.getOrPut(lowestPriorityOrder.customerId) { mutableListOf() }.add(waitingTime)
            }

            queue.add(order)
            orderStartTimes[order.id] = startTime
            logger.info("Removed order ${lowestPriorityOrder.id} from customer ${lowestPriorityOrder.customerId} to make space for order ${order.id} from customer ${order.customerId}")
            orderServiceClient.notifyOrderCancellation(lowestPriorityOrder)
            waitingOrderCount[order.customerId] = waitingOrderCount[order.customerId]?.inc() ?: 1
            return QueueResponse.ACCEPTED
        }

        val samePriorityOrder = queue.filter { it.customerId == order.customerId }
            .minByOrNull { it.addingTime }

        if (samePriorityOrder != null) {
            queue.remove(samePriorityOrder)
            val endTime = System.currentTimeMillis()
            orderEndsTimes[samePriorityOrder.id] = endTime
            val startTimeRemoved = orderStartTimes.remove(samePriorityOrder.id)
            if (startTimeRemoved != null) {
                val waitingTime = endTime - startTimeRemoved
                queueWaitingTimesPerSource.getOrPut(samePriorityOrder.customerId) { mutableListOf() }.add(waitingTime)
            }

            queue.add(order)
            orderStartTimes[order.id] = startTime
            logger.info("Removed order ${samePriorityOrder.id} from customer ${samePriorityOrder.customerId} to make space for newer order ${order.id} with the same priority")
            orderServiceClient.notifyOrderCancellation(samePriorityOrder)
            waitingOrderCount[order.customerId] = waitingOrderCount[order.customerId]?.inc() ?: 1
            return QueueResponse.ACCEPTED
        }

        logger.info("Rejected order ${order.id} from customer ${order.customerId} due to lower priority or full queue")
        orderServiceClient.notifyOrderCancellation(order)
        queueWaitingTimesPerSource.getOrPut(order.customerId) { mutableListOf() }.add(0L)
        waitingOrderCount[order.customerId] = waitingOrderCount[order.customerId]?.inc() ?: 1
        return QueueResponse.REJECTED
    }

    fun handleChefAvailability(chefId: Int) {
        synchronized(lock) {
            logger.info("Chef $chefId is now available")
            availableChefs++
            processNextOrderForChef()
        }
    }

    private fun processNextOrderForChef() {
        synchronized(lock) {
            while (availableChefs > 0 && queue.isNotEmpty()) {
                if (currentPackage.isNullOrEmpty()) {
                    currentPackage = formNewPackage()?.toMutableList()
                }

                if (!currentPackage.isNullOrEmpty()) {
                    val nextOrder = currentPackage!!.removeFirst()
                    queue.remove(nextOrder)
                    logger.info("Sending order ${nextOrder.id} to ChefService for processing")
                    val endTime = System.currentTimeMillis()
                    orderEndsTimes[nextOrder.id] = endTime

                    val startTime = orderStartTimes.remove(nextOrder.id)
                    if (startTime != null) {
                        val waitingTime = endTime - startTime
                        queueWaitingTimesPerSource.getOrPut(nextOrder.customerId) { mutableListOf() }.add(waitingTime)
                    } else {
                        queueWaitingTimesPerSource.getOrPut(nextOrder.customerId) { mutableListOf() }.add(0L)
                    }

                    sendOrderToChef(nextOrder)

                    if (currentPackage!!.isEmpty()) {
                        currentPackage = null
                        logger.info("Current package processed completely")
                    }
                } else {
                    logger.info("No valid packages to process")
                    break
                }
            }
        }
    }

    private fun sendOrderToChef(order: Order) {
        availableChefs--
        chefServiceClient.assignOrderToChef(order)
    }

    private fun formNewPackage(): List<Order>? {
        if (queue.isEmpty()) return null

        val groupedByCustomer = queue.groupBy { it.customerId }
        val highestPriorityGroup = groupedByCustomer.maxByOrNull { it.key }?.value

        return highestPriorityGroup?.also {
            logger.info("Formed new package with ${it.size} orders for customer ${it.first().customerId}")
        }
    }

    fun calculateAverageWaitingTimeBySource(): Map<Long, Double> {
        val averageWaitingTimes: MutableMap<Long, Double> = mutableMapOf()
        queueWaitingTimesPerSource.forEach { (customerId, times) ->
            if (times.isNotEmpty()) {
                val avg = times.sum().toDouble() / times.size
                averageWaitingTimes[customerId] = avg
            } else {
                averageWaitingTimes[customerId] = Double.NaN
            }
        }
        return averageWaitingTimes
    }

    fun calculateVarianceAndStdDevBySource(averageWaitingTimes: Map<Long, Double>): Map<Long, Pair<Double, Double>> {
        val varianceBySource: MutableMap<Long, Double> = mutableMapOf()

        queueWaitingTimesPerSource.forEach { (customerId, times) ->
            val avg = averageWaitingTimes[customerId] ?: 0.0
            val squaredSum = times.fold(0.0) { acc, t ->
                acc + (t - avg) * (t - avg)
            }

            val count = times.size
            val variance = if (count > 0) squaredSum / count else Double.NaN
            val stdDev = if (variance.isNaN()) Double.NaN else sqrt(variance)
            varianceBySource[customerId] = variance
        }

        val stdDevBySource: MutableMap<Long, Pair<Double, Double>> = mutableMapOf()
        varianceBySource.forEach { (customerId, variance) ->
            val stdDev = if (variance.isNaN()) Double.NaN else sqrt(variance)
            stdDevBySource[customerId] = Pair(variance, stdDev)
        }

        return stdDevBySource
    }

    @PreDestroy
    fun print() {
        val averageWaitingTimes = calculateAverageWaitingTimeBySource()
        val varianceAndStdDev = calculateVarianceAndStdDevBySource(averageWaitingTimes)

        println("\n===== Waiting Time Statistics by Source =====")
        println(String.format("%-15s%-20s%-20s", "Source", "Avg Waiting Time (ms)", "Std Dev"))
        println("-------------------------------------------------------")

        queueWaitingTimesPerSource.forEach { (customerId, _) ->
            val avgTime = averageWaitingTimes[customerId] ?: 0.0
            val variance = varianceAndStdDev[customerId]?.first ?: Double.NaN
            println(
                String.format(
                    "%-15d%-20.2f%-20.2f",
                    customerId,
                    avgTime,
                    sqrt(variance)
                )
            )
        }
        println("=======================================================")
    }
}
