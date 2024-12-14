package ru.spbstu.chefservice.service

import jakarta.annotation.PreDestroy
import mu.KotlinLogging
import org.springframework.stereotype.Service
import ru.spbstu.chefservice.client.OrderServiceClient
import ru.spbstu.chefservice.client.QueueServiceClient
import ru.spbstu.chefservice.config.ChefConfig
import ru.spbstu.chefservice.model.Chef
import ru.spbstu.chefservice.model.Order
import java.util.*
import kotlin.math.pow

@Service
class ChefService(
    private val orderServiceClient: OrderServiceClient,
    private val queueServiceClient: QueueServiceClient,
    private val chefConfig: ChefConfig
) {
    private val logger = KotlinLogging.logger {}
    private val chefs: MutableList<Chef> = mutableListOf()
    private var nextChefIndex = 0

    private val orderStartTimes: MutableMap<UUID, Long> = mutableMapOf()
    private val serviceTimesPerSource: MutableMap<Long, MutableList<Long>> = mutableMapOf()
    private val serviceStartTime: Long = System.currentTimeMillis()

    init {
        initializeChefs()
        notifyQueueAboutFreeChefs()
    }

    private fun initializeChefs() {
        repeat(chefConfig.count) { chefId ->
            chefs.add(
                Chef(
                    id = chefId,
                    processingTimeLambda = chefConfig.lambda,
                    notifyCompletion = { order -> notifyOrderCompletion(order, chefId) }
                )
            )
        }
    }

    fun processOrder(order: Order) {
        synchronized(this) {
            orderStartTimes[order.id] = System.currentTimeMillis()

            val startIndex = nextChefIndex
            do {
                val chef = chefs[nextChefIndex]
                if (chef.isAvailable()) {
                    chef.processOrder(order)
                    logger.info { "Assigned order ${order.id} to chef ${chef.id}" }
                    nextChefIndex = (nextChefIndex + 1) % chefs.size
                    return
                }
                nextChefIndex = (nextChefIndex + 1) % chefs.size
            } while (nextChefIndex != startIndex)

            logger.warn("No available chef for order ${order.id}")
        }
    }

    private fun notifyOrderCompletion(order: Order, chefId: Int) {
        logger.info { "Chef $chefId completed order ${order.id}" }
        orderServiceClient.notifyOrderCompletion(order.id)
        queueServiceClient.notifyChefAvailable(chefId)

        val endTime = System.currentTimeMillis()
        val startTime = orderStartTimes.remove(order.id)
        if (startTime != null) {
            val duration = endTime - startTime
            val sourceId = order.customerId
            val times = serviceTimesPerSource.getOrPut(sourceId) { mutableListOf() }
            times.add(duration)
        }
    }

    private fun notifyQueueAboutFreeChefs() {
        chefs.filter { it.isAvailable() }.forEach {
            queueServiceClient.notifyChefAvailable(it.id)
        }
    }

    fun calculateAverage(sourceId: Long): Double {
        val times = serviceTimesPerSource[sourceId]?.toList() ?: return Double.NaN
        if (times.isEmpty()) return Double.NaN
        val sum = times.sum()
        return sum.toDouble() / times.size
    }

    fun calculateVariance(sourceId: Long): Double {
        val times = serviceTimesPerSource[sourceId]?.toList() ?: return Double.NaN
        val n = times.size
        if (n < 2) return Double.NaN

        val avg = calculateAverage(sourceId)
        val sumOfSquares = times.fold(0.0) { acc, t ->
            acc + (t - avg).pow(2)
        }

        return sumOfSquares / (n - 1)
    }

    @PreDestroy
    fun printAllStatistics() {
        println("\n===== Cooking Time Statistics by Source =====")
        println(String.format("%-15s%-20s%-20s%-20s", "Source", "Count", "Avg Cooking Time (ms)", "Std Dev"))
        println("------------------------------------------------------------------")

        for ((sourceId, times) in serviceTimesPerSource) {
            val avg = calculateAverage(sourceId)
            val variance = calculateVariance(sourceId)
            val stdDev = if (variance.isNaN()) Double.NaN else kotlin.math.sqrt(variance)

            val stdDevDisplay = if (stdDev.isNaN()) "-" else String.format("%.2f", stdDev)

            println(
                String.format(
                    "%-15s%-20d%-20.2f%-20s",
                    sourceId,
                    times.size,
                    avg,
                    stdDevDisplay
                )
            )
        }
        println("===============================================================")

        val totalServiceTime = System.currentTimeMillis() - serviceStartTime
        println("\n===== Chef Utilization =====")
        println(String.format("%-10s%-20s%-20s", "Chef", "Total Working (ms)", "Utilization"))
        println("--------------------------------------------------")

        for (chef in chefs) {
            val utilization = chef.getTotalWorkingTimeMs().toDouble() / totalServiceTime
            println(
                String.format(
                    "%-10d%-20d%-20.4f",
                    chef.id,
                    chef.getTotalWorkingTimeMs(),
                    utilization
                )
            )
        }

        println("\nApplication total running time: $totalServiceTime ms")
    }
}
