package ru.spbstu.chefservice.model

import java.time.LocalDateTime
import java.util.*

data class Order(
    val id: UUID,
    val customerId: Long,
    val priority: Long,
    val items: List<String>,
    var status: Status,
    val addingTime: LocalDateTime = LocalDateTime.now()
)
