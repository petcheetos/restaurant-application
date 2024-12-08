package ru.spbstu.chefservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ChefServiceApplication

fun main(args: Array<String>) {
    runApplication<ChefServiceApplication>(*args)
}
