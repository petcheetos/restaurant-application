package ru.spbstu.queueservice.controller

import org.springframework.web.bind.annotation.*
import ru.spbstu.queueservice.model.Order
import ru.spbstu.queueservice.model.QueueResponse
import ru.spbstu.queueservice.service.QueueService

@RestController
@RequestMapping("/api/queue")
class QueueController(private val queueService: QueueService) {

    @PostMapping("/push")
    fun addOrder(@RequestBody order: Order): QueueResponse {
        return queueService.addOrder(order)
    }

    @PostMapping("/chef-available")
    fun chefAvailable(@RequestParam chefId: Int) {
        queueService.handleChefAvailability(chefId)
    }
}
