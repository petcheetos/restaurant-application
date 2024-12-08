package ru.spbstu.orderservice.generator

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "order.generator")
class OrderGeneratorConfig {
    var enabled: Boolean = true
    var numberOfSenders: Int = 5
    var interval: Interval = Interval()

    class Interval {
        var min: Long = 5000
        var max: Long = 20000
    }
}
