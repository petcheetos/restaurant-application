package ru.spbstu.queueservice.client

import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import ru.spbstu.queueservice.model.Order

@Component
class ChefServiceClient {
    private val logger = KotlinLogging.logger {}
    private val webClient = WebClient.create("http://localhost:8083/api/chefs")

    fun assignOrderToChef(order: Order) {
        webClient.post()
            .uri("/process-order")
            .bodyValue(order)
            .retrieve()
            .bodyToMono(Void::class.java)
            .doOnError { error ->
                logger.error("Failed to assign order ${order.id} to a chef", error)
            }
            .subscribe()
    }
}
