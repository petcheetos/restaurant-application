package ru.spbstu.orderservice.service

import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Service
import ru.spbstu.orderservice.model.Order
import ru.spbstu.orderservice.model.Status
import ru.spbstu.orderservice.repository.OrderRepository
import ru.spbstu.orderservice.statistics.StatisticsService
import java.time.LocalDateTime
import java.util.*

@Service
class OrderService(
    private val repository: OrderRepository,
    private val statisticsService: StatisticsService,
) {
    private val queue: MutableList<Order> = mutableListOf()
    private val lock = Any()

    fun saveOrder(order: Order) {
        repository.saveOrder(order)
    }

    fun updateWithStatus(orderId: UUID, status: Status) {
        if (status != Status.IN_QUEUE && status != Status.NEW) {
            val customerId = getCustomerIdByOrderId(orderId)
            val createdAt = getCreatedAtByOrderId(orderId)
            val timeInSystem = System.currentTimeMillis() - createdAt.toEpochMilli()

            statisticsService.recordOrderProcessingTime(customerId, timeInSystem)
        }
        repository.updateOrderStatus(orderId, status)
    }

    fun getCustomerIdByOrderId(orderId: UUID): Long {
        return repository.getCustomerIdByOrderId(orderId)
    }

    fun getCreatedAtByOrderId(orderId: UUID): java.time.Instant {
        return repository.getCreatedAtByOrderId(orderId)
    }

    @PreDestroy
    fun onShutdown() {
        synchronized(lock) {
            val remainingOrders = queue.toList()
            val currentTime = LocalDateTime.now()
            statisticsService.recordRemainingOrders(remainingOrders, currentTime)
        }
    }
}
