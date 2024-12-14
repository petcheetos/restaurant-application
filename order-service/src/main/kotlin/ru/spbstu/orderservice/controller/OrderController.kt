package ru.spbstu.orderservice.controller

import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.spbstu.orderservice.model.Status
import ru.spbstu.orderservice.service.OrderService
import ru.spbstu.orderservice.statistics.StatisticsService
import java.util.*

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService,
    private val statisticsService: StatisticsService
) {
    private val logger = KotlinLogging.logger {}

    @PostMapping("/{orderId}/canceled")
    @ResponseStatus(HttpStatus.OK)
    fun cancelOrder(@PathVariable orderId: UUID) {
        logger.info("Received cancel request for order $orderId")
        try {
            val customerId = orderService.getCustomerIdByOrderId(orderId)
            statisticsService.incrementCanceledCount(orderId, customerId)
            orderService.updateWithStatus(orderId, Status.CANCELED)
            logger.info("Order $orderId status updated to CANCELED")
        } catch (e: Exception) {
            logger.error(e) { "Failed to cancel order $orderId" }
            throw RuntimeException("Failed to cancel order $orderId", e)
        }
    }

    @PostMapping("/{orderId}/completed")
    @ResponseStatus(HttpStatus.OK)
    fun completeOrder(@PathVariable orderId: UUID) {
        logger.info("Received completion notification for order $orderId")
        try {
            orderService.updateWithStatus(orderId, Status.COMPLETED)
            logger.info("Order $orderId status updated to COMPLETED")
        } catch (e: Exception) {
            logger.error(e) { "Failed to complete order $orderId" }
            throw RuntimeException("Failed to complete order $orderId", e)
        }
    }
}
