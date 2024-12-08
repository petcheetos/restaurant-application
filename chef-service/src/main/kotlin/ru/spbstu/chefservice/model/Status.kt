package ru.spbstu.chefservice.model

enum class Status {
    NEW,
    IN_QUEUE,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    CANCELED
}