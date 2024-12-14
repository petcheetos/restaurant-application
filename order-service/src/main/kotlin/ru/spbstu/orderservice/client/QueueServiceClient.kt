package ru.spbstu.orderservice.client

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import ru.spbstu.orderservice.model.Order
import ru.spbstu.orderservice.model.QueueResponse
import ru.spbstu.orderservice.statistics.StatisticsService

@Component
class QueueServiceClient(
    private val webClientBuilder: WebClient.Builder,
    private val statisticsService: StatisticsService,
    @Value("\${queue.service.url}") private val queueServiceUrl: String
) {
    private val logger = KotlinLogging.logger {}

    fun sendToQueue(order: Order): QueueResponse {
        return try {
            webClientBuilder
                .baseUrl(queueServiceUrl)
                .build()
                .post()
                .uri("/push")
                .bodyValue(order)
                .retrieve()
                .bodyToMono(QueueResponse::class.java)
                .block() ?: QueueResponse.REJECTED
        } catch (e: Exception) {
            val customerId = order.customerId
            statisticsService.incrementRejectedCount(order.id, customerId)
            logger.error(e) { "Failed to send order to the queue: $order" }
            QueueResponse.REJECTED
        }
    }
}
