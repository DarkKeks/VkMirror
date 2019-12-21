package ru.darkkeks.vkmirror.util

import kotlin.reflect.KClass
import kotlin.reflect.full.cast

typealias Handler<T> = (T) -> Unit

class MessageHandlerHolder<M : Any, T : M>(
        private val klass: KClass<T>,
        private val handler: Handler<T>
) {
    fun acceptMessage(message: M) = handler(klass.cast(message))
}

class EventHandler<M : Any> {
    private val handlers = mutableMapOf<KClass<*>, MutableList<MessageHandlerHolder<M, *>>>()

    fun <T : M> addHandler(klass: KClass<T>, handler: Handler<T>) {
        handlers.computeIfAbsent(klass) { mutableListOf() }.add(MessageHandlerHolder(klass, handler))
    }

    fun <T : M> onMessage(message: T) {
        val klass = message::class
        handlers[klass]?.forEach {
            it.acceptMessage(message)
        }
    }
}