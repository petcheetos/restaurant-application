package ru.spbstu.queueservice.client

import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import ru.spbstu.queueservice.model.Order

@Component
class OrderServiceClient {
    private val logger = KotlinLogging.logger {}
    private val webClient = WebClient.create("http://localhost:8081/api/orders")

    fun notifyOrderCancellation(order: Order) {
        webClient.post()
            .uri("/${order.id}/canceled")
            .retrieve()
            .bodyToMono(Void::class.java)
            .doOnSuccess {
                logger.info("Notified OrderService about cancellation of order ${order.id}")
            }
            .onErrorResume { error ->
                logger.error("Failed to notify OrderService about cancellation of order ${order.id}", error)
                null
            }
            .subscribe()
    }
}
