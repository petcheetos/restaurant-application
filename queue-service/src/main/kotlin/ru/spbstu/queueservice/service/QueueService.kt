package ru.spbstu.queueservice.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import ru.spbstu.queueservice.client.ChefServiceClient
import ru.spbstu.queueservice.client.OrderServiceClient
import ru.spbstu.queueservice.config.QueueConfig
import ru.spbstu.queueservice.model.Order
import ru.spbstu.queueservice.model.QueueResponse

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
        if (queue.size < queueConfig.size) {
            queue.add(order)
            logger.info("Added order ${order.id} from customer ${order.customerId}. Space left: ${queueConfig.size - queue.size}")
            return QueueResponse.ACCEPTED
        }

        val lowestPriorityOrder = queue.minWithOrNull(compareBy<Order> { it.customerId }.thenBy { it.addingTime })
        if (lowestPriorityOrder != null && lowestPriorityOrder.customerId < order.customerId) {
            queue.remove(lowestPriorityOrder)
            queue.add(order)
            logger.info("Removed order ${lowestPriorityOrder.id} from customer ${lowestPriorityOrder.customerId} to make space for order ${order.id} from customer ${order.customerId}")
            orderServiceClient.notifyOrderCancellation(lowestPriorityOrder)
            return QueueResponse.ACCEPTED
        }

        val samePriorityOrder = queue.filter { it.customerId == order.customerId }
            .minByOrNull { it.addingTime }

        if (samePriorityOrder != null) {
            queue.remove(samePriorityOrder)
            queue.add(order)
            logger.info("Removed order ${samePriorityOrder.id} from customer ${samePriorityOrder.customerId} to make space for newer order ${order.id} with the same priority")
            orderServiceClient.notifyOrderCancellation(samePriorityOrder)
            return QueueResponse.ACCEPTED
        }

        logger.info("Rejected order ${order.id} from customer ${order.customerId} due to lower priority or full queue")
        orderServiceClient.notifyOrderCancellation(order)
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
}

