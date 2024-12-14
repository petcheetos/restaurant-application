package ru.spbstu.orderservice.statistics

import org.springframework.stereotype.Service
import ru.spbstu.orderservice.model.Order
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@Service
class StatisticsService {
    private val generatedOrdersCount: MutableMap<Long, Int> = mutableMapOf()
    private val rejectedOrdersCount: MutableMap<Long, Int> = mutableMapOf()
    private val canceledOrdersCount: MutableMap<Long, Int> = mutableMapOf()
    private val totalRejectedOrders: MutableSet<UUID> = mutableSetOf()
    private val totalTimeInSystem: MutableMap<Long, Long> =
        mutableMapOf() // суммарное время пребывания заявок от каждого источника
    private val processedOrdersCount: MutableMap<Long, Int> =
        mutableMapOf() // количество обработанных заявок для каждого источника

    fun recordOrderProcessingTime(customerId: Long, timeInSystem: Long) {
        synchronized(this) {
            totalTimeInSystem[customerId] = totalTimeInSystem.getOrDefault(customerId, 0L) + timeInSystem
            processedOrdersCount[customerId] = processedOrdersCount.getOrDefault(customerId, 0) + 1
        }
    }

    fun incrementGeneratedCount(customerId: Long) {
        synchronized(generatedOrdersCount) {
            generatedOrdersCount[customerId] = generatedOrdersCount.getOrDefault(customerId, 0) + 1
        }
    }

    fun incrementRejectedCount(orderId: UUID, customerId: Long) {
        synchronized(totalRejectedOrders) {
            if (!totalRejectedOrders.contains(orderId)) {
                totalRejectedOrders.add(orderId)
                synchronized(rejectedOrdersCount) {
                    rejectedOrdersCount[customerId] = rejectedOrdersCount.getOrDefault(customerId, 0) + 1
                }
            }
        }
    }

    fun incrementCanceledCount(orderId: UUID, customerId: Long) {
        synchronized(totalRejectedOrders) {
            if (!totalRejectedOrders.contains(orderId)) {
                totalRejectedOrders.add(orderId)
                synchronized(canceledOrdersCount) {
                    canceledOrdersCount[customerId] = canceledOrdersCount.getOrDefault(customerId, 0) + 1
                }
            }
        }
    }

    fun getGeneratedCount(customerId: Long): Int {
        return synchronized(generatedOrdersCount) {
            generatedOrdersCount.getOrDefault(customerId, 0)
        }
    }

    fun getRejectedCount(customerId: Long): Int {
        return synchronized(rejectedOrdersCount) {
            rejectedOrdersCount.getOrDefault(customerId, 0)
        }
    }

    fun getCanceledCount(customerId: Long): Int {
        return synchronized(canceledOrdersCount) {
            canceledOrdersCount.getOrDefault(customerId, 0)
        }
    }

    fun getAverageTimeInSystem(customerId: Long): Double {
        val total = totalTimeInSystem[customerId] ?: 0L
        val count = processedOrdersCount[customerId] ?: 0
        return if (count > 0) total.toDouble() / count else 0.0
    }

    fun printStatistics() {
        printCommonStatistic()
        printAverageTimeInSystem()

    }

    private fun printCommonStatistic() {
        println("\n===== Order Statistics =====")
        println(
            String.format(
                "%-15s%-20s%-20s%-20s%-20s%-20s",
                "Source Number",
                "Orders Generated",
                "Rejected",
                "Canceled",
                "Total Rejections",
                "Rejection Probability (%)"
            )
        )
        println("--------------------------------------------------------------------------------------------")
        val allCustomers = (generatedOrdersCount.keys + rejectedOrdersCount.keys + canceledOrdersCount.keys).sorted()
        allCustomers.forEach { customerId ->
            val generated = getGeneratedCount(customerId)
            val rejected = getRejectedCount(customerId)
            val canceled = getCanceledCount(customerId)
            val totalRejections = rejected + canceled
            val rejectionProbability = if (generated > 0) totalRejections.toDouble() / generated else 0.0
            println(
                String.format(
                    "%-15s%-20s%-20s%-20s%-20s%-20.2f",
                    customerId,
                    generated,
                    rejected,
                    canceled,
                    totalRejections,
                    rejectionProbability * 100
                )
            )
        }
        println("============================================================================================")
    }

    private fun printAverageTimeInSystem() {
        println("\n===== Average Time in System Statistics =====")
        println(String.format("%-15s%-20s", "Source Number", "Avg Time in System (ms)"))
        println("-------------------------------------------")
        totalTimeInSystem.keys.forEach { customerId ->
            val avgTime = getAverageTimeInSystem(customerId)
            println(String.format("%-15s%-20.2f", customerId, avgTime))
        }
        println("===========================================")
    }
    fun recordRemainingOrders(queueOrders: List<Order>, currentTime: LocalDateTime) {
        synchronized(this) {
            queueOrders.forEach { order ->
                val customerId = order.customerId
                val timeInQueue = ChronoUnit.MILLIS.between(order.addingTime, currentTime)
                totalTimeInSystem[customerId] = totalTimeInSystem.getOrDefault(customerId, 0L) + timeInQueue
                processedOrdersCount[customerId] = processedOrdersCount.getOrDefault(customerId, 0) + 1
            }
        }
    }
}
