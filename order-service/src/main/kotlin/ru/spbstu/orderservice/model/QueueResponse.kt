package ru.spbstu.orderservice.model

enum class QueueResponse {
    ACCEPTED,
    REJECTED, // не взяли в очередь
    CANCELED // вытиснули из очереди
}
