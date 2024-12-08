package ru.spbstu.orderservice.repository

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import ru.spbstu.orderservice.model.Order
import ru.spbstu.orderservice.table.OrdersTable

val objectMapper = jacksonObjectMapper()

fun ResultRow.toOrder(): Order =
    Order(
        id = this[OrdersTable.id],
        customerId = this[OrdersTable.customerId],
        priority = this[OrdersTable.priority],
        items = objectMapper.readValue(this[OrdersTable.items]),
        status = this[OrdersTable.status]
    )

fun Order.toInsertRow(): Map<Column<*>, Any?> {
    return mapOf(
        OrdersTable.id to id,
        OrdersTable.customerId to customerId,
        OrdersTable.priority to priority,
        OrdersTable.items to objectMapper.writeValueAsString(items),
        OrdersTable.status to status,
        OrdersTable.createdAt to java.time.LocalDateTime.now()
    )
}