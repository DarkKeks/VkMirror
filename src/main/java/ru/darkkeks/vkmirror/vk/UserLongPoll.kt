package ru.darkkeks.vkmirror.vk

import com.google.gson.JsonElement
import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.exceptions.LongPollServerKeyExpiredException
import com.vk.api.sdk.objects.messages.LongpollParams
import ru.darkkeks.vkmirror.vk.`object`.Message


private const val WAIT_TIME_SECONDS = 25

class UserLongPoll(
        private var client: VkApiClient,
        private val actor: UserActor,
        private val listener: (Message) -> Unit) {

    fun run() {
        var longPollServer = getLongPollServer()
        var lastTimeStamp = longPollServer.ts

        while (true) {
            try {
                val events = GetUserLongPollEventsQuery(
                        client = client,
                        url = "https://${longPollServer.server}",
                        key = longPollServer.key,
                        ts = lastTimeStamp)
                        .mode(2 or 8 or 64)
                        .version(3)
                        .waitTime(WAIT_TIME_SECONDS)
                        .execute()

                lastTimeStamp = events.ts
                events.updates.forEach { parse(it) }
            } catch (e: LongPollServerKeyExpiredException) {
                longPollServer = getLongPollServer()
            }
        }
    }

    private fun getLongPollServer(): LongpollParams {
        return client.messages().getLongPollServer(actor)
                .lpVersion(3)
                .execute()
    }

    private fun parse(update: JsonElement) {
        val updateArray = update.asJsonArray
        when (updateArray.first().asInt) {
            4 -> listener(client.gson.fromJson(update, Message::class.java))
        }
    }
}