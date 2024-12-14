package ru.spbstu.orderservice.repository

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.springframework.stereotype.Repository
import ru.spbstu.orderservice.model.Order
import ru.spbstu.orderservice.model.Status
import ru.spbstu.orderservice.table.OrdersTable
import java.util.*

@Repository
class OrderRepository {

    fun saveOrder(order: Order) {
        transaction {
            OrdersTable.insert {
                it[id] = order.id
                it[customerId] = order.customerId
                it[priority] = order.priority
                it[items] = objectMapper.writeValueAsString(order.items)
                it[status] = order.status
                it[createdAt] = java.time.LocalDateTime.now(java.time.Clock.systemUTC())
            }
        }
    }

    fun updateOrderStatus(orderId: UUID, newStatus: Status) {
        transaction {
            OrdersTable.update({ OrdersTable.id eq orderId }) {
                it[status] = newStatus
            }
        }
    }

    fun getCustomerIdByOrderId(orderId: UUID): Long = transaction {
        OrdersTable.select { OrdersTable.id eq orderId }
            .map { it[OrdersTable.customerId] }
            .firstOrNull() ?: throw RuntimeException("Order not found: $orderId")
    }

    fun getCreatedAtByOrderId(orderId: UUID): java.time.Instant = transaction {
        OrdersTable.select { OrdersTable.id eq orderId }
            .map { it[OrdersTable.createdAt].toInstant(java.time.ZoneOffset.UTC) }
            .firstOrNull() ?: throw RuntimeException("Order not found: $orderId")
    }
}
