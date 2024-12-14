package ru.spbstu.chefservice.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.exp

class Chef(
    val id: Int,
    private val processingTimeLambda: Double,
    private val notifyCompletion: (Order) -> Unit
) {
    private val logger = KotlinLogging.logger {}
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var isBusy: Boolean = false

    private val totalWorkingTimeMs = AtomicLong(0)

    private var ordersProcessed = 0

    private val baseTime = 1000L
    private val growthFactor = 0.3

    fun isAvailable(): Boolean = !isBusy

    fun processOrder(order: Order) {
        if (isBusy) {
            logger.warn { "Chef $id is already busy!" }
            return
        }

        isBusy = true
        coroutineScope.launch {
            try {
                val currentOrderIndex = ordersProcessed
                ordersProcessed++

                val processingTime = (baseTime * exp(growthFactor * currentOrderIndex)).toLong()

                logger.info { "Chef $id started processing order ${order.id} (processing time: $processingTime ms, ordersProcessed=$ordersProcessed)" }

                delay(processingTime)
                totalWorkingTimeMs.addAndGet(processingTime)
                isBusy = false
                notifyCompletion(order)
            } catch (e: Exception) {
                logger.error(e) { "Error while processing order ${order.id} by chef $id" }
                isBusy = false
            }
        }
    }

    fun getTotalWorkingTimeMs(): Long = totalWorkingTimeMs.get()
}
