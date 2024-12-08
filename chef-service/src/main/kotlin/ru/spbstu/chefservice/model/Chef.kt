package ru.spbstu.chefservice.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.math.ln
import kotlin.random.Random

class Chef(
    val id: Int,
    private val processingTimeLambda: Double,
    private val notifyCompletion: (Order) -> Unit
) {
    private val logger = KotlinLogging.logger {}
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var isBusy: Boolean = false
    fun isAvailable(): Boolean = !isBusy

    fun processOrder(order: Order) {
        if (isBusy) {
            logger.warn { "Chef $id is already busy!" }
            return
        }

        isBusy = true
        coroutineScope.launch {
            try {
                val processingTime = generateExponentialTime(processingTimeLambda)
                logger.info { "Chef $id started processing order ${order.id} (processing time: $processingTime ms)" }

                delay(processingTime)

                isBusy = false
                notifyCompletion(order)
            } catch (e: Exception) {
                logger.error(e) { "Error while processing order ${order.id} by chef $id" }
                isBusy = false
            }
        }
    }

    private fun generateExponentialTime(lambda: Double): Long {
        val randomValue = Random.nextDouble()
        return (-ln(1.0 - randomValue) / lambda).toLong()
    }
}
