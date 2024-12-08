package ru.spbstu.orderservice.generator

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import ru.spbstu.orderservice.client.QueueServiceClient
import ru.spbstu.orderservice.model.Order
import ru.spbstu.orderservice.model.QueueResponse
import ru.spbstu.orderservice.model.Status
import ru.spbstu.orderservice.service.OrderService
import java.util.*
import kotlin.random.Random

@Component
@ConditionalOnProperty(
    prefix = "order.generator",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class OrderGenerator(
    private val orderService: OrderService,
    private val queueServiceClient: QueueServiceClient,
    private val generatorConfig: OrderGeneratorConfig
) {

    private val logger = KotlinLogging.logger {}
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private val sources = (1L..generatorConfig.numberOfSenders.toLong()).toList()

    @PostConstruct
    fun startGenerator() {
        println("Generator is running")

        sources.forEach { sourceNumber ->
            logger.info("Created source with number (priority): $sourceNumber")
        }

        coroutineScope.launch {
            generateOrders()
        }
    }

    private suspend fun generateOrders() {
        while (true) {
            val delayTime = Random.nextLong(
                generatorConfig.interval.min,
                generatorConfig.interval.max
            )
            delay(delayTime)

            val sourceNumber = sources.random()

            val order = createRandomOrder(sourceNumber)
            logger.info("Generated order: $order from source number: $sourceNumber")

            processOrder(order)
        }
    }

    private fun createRandomOrder(sourceNumber: Long): Order {
        return Order(
            id = UUID.randomUUID(),
            customerId = sourceNumber,
            priority = sourceNumber,
            items = listOf("Pizza", "Burger", "Drink").shuffled().take(2),
            status = Status.NEW
        )
    }

    private fun processOrder(order: Order) {
        orderService.saveOrder(order)

        val response = queueServiceClient.sendToQueue(order)
        logger.info("Order ${order.id} sent to QueueService with response: $response")

        val updatedStatus = when (response) {
            QueueResponse.ACCEPTED -> Status.IN_QUEUE
            QueueResponse.REJECTED -> Status.REJECTED
            QueueResponse.CANCELED -> Status.CANCELED
        }

        orderService.updateWithStatus(order.id, updatedStatus)
        logger.info("Order ${order.id} processed with status: $updatedStatus")
    }

}
