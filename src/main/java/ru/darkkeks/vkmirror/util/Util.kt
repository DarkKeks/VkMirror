package ru.darkkeks.vkmirror.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline fun <reified T> createLogger(): Logger {
    return LoggerFactory.getLogger(T::class.java)
}

fun prompt(message: String): String {
    print(message)
    print(": ")
    return readLine() ?: ""
}

fun getEnv(name: String): String {
    return System.getenv(name) ?: throw IllegalStateException("No env variable $name")
}

class ListBuilder<T> {
    private val result = mutableListOf<T>()

    fun add(vararg values: T) {
        values.forEach {
            result.add(it)
        }
    }

    fun build() = result.toList()
}

inline fun <T> buildList(block: ListBuilder<T>.() -> Unit): List<T> {
    val builder = ListBuilder<T>()
    builder.block()
    return builder.build()
}


fun JsonArray.getNullable(index: Int): JsonElement? {
    return if (size() <= index) null else get(index)
}