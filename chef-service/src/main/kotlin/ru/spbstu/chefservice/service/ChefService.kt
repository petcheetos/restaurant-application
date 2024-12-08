package ru.spbstu.chefservice.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import ru.spbstu.chefservice.client.OrderServiceClient
import ru.spbstu.chefservice.client.QueueServiceClient
import ru.spbstu.chefservice.config.ChefConfig
import ru.spbstu.chefservice.model.Chef
import ru.spbstu.chefservice.model.Order

@Service
class ChefService(
    private val orderServiceClient: OrderServiceClient,
    private val queueServiceClient: QueueServiceClient,
    private val chefConfig: ChefConfig
) {
    private val logger = KotlinLogging.logger {}
    private val chefs: MutableList<Chef> = mutableListOf()
    private var nextChefIndex = 0

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
    }

    private fun notifyQueueAboutFreeChefs() {
        chefs.filter { it.isAvailable() }.forEach {
            queueServiceClient.notifyChefAvailable(it.id)
        }
    }
}
