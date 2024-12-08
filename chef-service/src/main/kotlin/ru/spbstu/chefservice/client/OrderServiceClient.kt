package ru.spbstu.chefservice.client

import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.util.*

@Component
class OrderServiceClient {
    private val logger = KotlinLogging.logger {}
    private val webClient = WebClient.create("http://localhost:8081/api/orders")

    fun notifyOrderCompletion(orderId: UUID) {
        webClient.post()
            .uri("/$orderId/completed")
            .retrieve()
            .bodyToMono(Void::class.java)
            .doOnSuccess {
                logger.info("Notified OrderService about completion of order $orderId")
            }
            .onErrorResume { error ->
                logger.error("Failed to notify OrderService about order $orderId", error)
                null
            }
            .subscribe()
    }
}
