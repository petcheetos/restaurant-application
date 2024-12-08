package ru.spbstu.orderservice.model

enum class Status {
    NEW,
    IN_QUEUE,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    CANCELED
}