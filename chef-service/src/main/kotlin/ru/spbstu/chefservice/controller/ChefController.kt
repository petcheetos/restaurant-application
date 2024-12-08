package ru.spbstu.chefservice.controller

import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.spbstu.chefservice.model.Order
import ru.spbstu.chefservice.service.ChefService

@RestController
@RequestMapping("/api/chefs")
class ChefController(
    private val chefService: ChefService
) {
    private val logger = KotlinLogging.logger {}

    @PostMapping("/process-order")
    @ResponseStatus(HttpStatus.OK)
    fun processOrder(@RequestBody order: Order) {
        logger.info { "Received order ${order.id} for processing" }
        chefService.processOrder(order)
    }
}
