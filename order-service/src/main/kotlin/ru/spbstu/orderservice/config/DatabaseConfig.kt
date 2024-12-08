package ru.spbstu.orderservice.config

import jakarta.annotation.PostConstruct
import org.jetbrains.exposed.sql.Database
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "spring.datasource")
class DataSourceProperties {
    lateinit var url: String
    lateinit var username: String
    lateinit var password: String
}

@Configuration
@EnableConfigurationProperties(DataSourceProperties::class)
class DatabaseConfig(private val dataSourceProperties: DataSourceProperties) {

    @PostConstruct
    fun init() {
        Database.connect(
            url = dataSourceProperties.url,
            driver = "org.postgresql.Driver",
            user = dataSourceProperties.username,
            password = dataSourceProperties.password
        )
    }
}

