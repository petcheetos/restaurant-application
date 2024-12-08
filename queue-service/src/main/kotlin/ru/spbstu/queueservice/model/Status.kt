package ru.spbstu.queueservice.model

enum class Status {
    NEW,
    IN_QUEUE,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    CANCELED
}