package ru.spbstu.orderservice.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import ru.spbstu.orderservice.model.Status

object OrdersTable : Table("orders") {
    val id = uuid("id")
    val customerId = long("customer_id")
    val priority = long("priority")
    val items = text("items")
    val status = enumerationByName("status", 50, Status::class)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}
