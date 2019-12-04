package ru.darkkeks.vkmirror.vk

import com.vk.api.sdk.callback.longpoll.LongPollQueryBuilder
import com.vk.api.sdk.client.AbstractQueryBuilder


const val MULTICHAT_BASE = 2_000_000_000
const val GROUP_CHAT_BASE = 1_000_000_000

/**
 * Multichat is a chat with multiple people (or group bots)
 * @return true if chat with that id is a multichat, false otherwise
 */
fun isMultichat(id: Int): Boolean {
    return id >= MULTICHAT_BASE
}

/**
 * @return true if chat with that id is a private chat with group
 */
fun isGroup(id: Int): Boolean {
    return id < 0
}

fun isPrivateChat(id: Int): Boolean {
    return !isMultichat(id) && !isGroup(id)
}


fun getChatUrl(peerId: Int): String? {
    if (isMultichat(peerId)) {
        return "https://vk.com/im?sel=c${peerId - MULTICHAT_BASE}"
    } else if (isGroup(peerId)) {
        return "https://vk.com/im?sel=-${peerId - GROUP_CHAT_BASE}"
    }
    return "https://vk.com/im?sel=${peerId}"
}


fun <T, R> LongPollQueryBuilder<T, R>.unsafeParameter(key: String, value: String): T = unsafeParam(key, value)

fun <T, R> LongPollQueryBuilder<T, R>.unsafeParameter(key: String, value: Int): T = unsafeParam(key, value)

fun <T, R> AbstractQueryBuilder<T, R>.unsafeParameter(key: String, vararg value: Int): T = unsafeParam(key, value)

fun <T, R, K> AbstractQueryBuilder<T, R>.unsafeParameter(key: String, value: K): T = unsafeParam(key, value)

fun <T, R, K> AbstractQueryBuilder<T, R>.unsafeParameter(key: String, vararg value: K): T = unsafeParam(key, *value)
