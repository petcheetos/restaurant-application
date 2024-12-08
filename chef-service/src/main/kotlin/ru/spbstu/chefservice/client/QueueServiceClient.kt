package ru.spbstu.chefservice.client

import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class QueueServiceClient {

    private val logger = KotlinLogging.logger {}
    private val webClient = WebClient.create("http://localhost:8082/api/queue")

    fun notifyChefAvailable(chefId: Int) {
        webClient.post()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/chef-available")
                    .queryParam("chefId", chefId)
                    .build()
            }
            .retrieve()
            .bodyToMono(Void::class.java)
            .doOnSuccess {
                logger.info("Successfully notified QueueService about availability of Chef $chefId")
            }
            .doOnError { error ->
                logger.error("Failed to notify QueueService about Chef $chefId", error)
            }
            .subscribe()
    }
}
