package ru.spbstu.chefservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "chef")
class ChefConfig {
    var count: Int = 5
    var lambda: Double = 0.2
}
