package ru.spbstu.queueservice.model

enum class QueueResponse {
    ACCEPTED,
    REJECTED, // не взяли в очередь
    CANCELED // вытиснули из очереди
}
