package ru.darkkeks.vkmirror.vk

import com.vk.api.sdk.callback.longpoll.LongPollQueryBuilder
import com.vk.api.sdk.callback.objects.longpoll.GetLongPollEventsActInfo
import com.vk.api.sdk.client.VkApiClient

class GetUserLongPollEventsQuery(client: VkApiClient, url: String, key: String, ts: Int) :
        LongPollQueryBuilder<GetUserLongPollEventsQuery, GetUserLongPollEventsResponse>(
                client, url, GetUserLongPollEventsResponse::class.java) {

    init {
        act(GetLongPollEventsActInfo.CHECK)
        key(key)
        ts(ts)
    }

    fun waitTime(value: Int) = unsafeParameter("wait", value)

    fun ts(value: Int) = unsafeParameter("ts", value)

    fun act(actInfo: GetLongPollEventsActInfo) = unsafeParameter("act", actInfo.value)

    fun mode(value: Int) = unsafeParameter("mode", value)

    fun version(value: Int) = unsafeParameter("version", value)

    override fun key(value: String) = unsafeParameter("key", value)

    override fun getThis() = this

    override fun essentialKeys() = listOf("act", "key", "ts")
}