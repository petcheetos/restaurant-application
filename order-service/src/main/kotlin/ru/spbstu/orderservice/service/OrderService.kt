package ru.spbstu.orderservice.service

import org.springframework.stereotype.Service
import ru.spbstu.orderservice.model.Order
import ru.spbstu.orderservice.model.Status
import ru.spbstu.orderservice.repository.OrderRepository
import java.util.*

@Service
class OrderService(
    private val repository: OrderRepository,
) {

    fun saveOrder(order: Order) {
        repository.saveOrder(order)
    }

    fun updateWithStatus(orderId: UUID, status: Status) {
        repository.updateOrderStatus(orderId, status)
    }

}
