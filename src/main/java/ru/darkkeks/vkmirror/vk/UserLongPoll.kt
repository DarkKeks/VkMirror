package ru.darkkeks.vkmirror.vk

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.exceptions.LongPollServerKeyExpiredException
import com.vk.api.sdk.objects.messages.LongpollParams
import ru.darkkeks.vkmirror.util.createLogger
import ru.darkkeks.vkmirror.vk.`object`.Message
import java.lang.reflect.Type


private const val WAIT_TIME_SECONDS = 25

interface UserLongPollListener {
    fun newMessage(message: Message)
    fun editMessage(editedMessage: Message)
    fun readUpToInbound(event: MessageReadUpTo)
    fun readUpToOutbound(event: MessageReadUpTo)
    fun userIsTyping(userId: Int)
    fun userIsTyping(event: UserIsTyping)
    fun usersAreTyping(event: UsersAreTyping)
}

class UserLongPoll(private var client: VkApiClient,
                   private val actor: UserActor,
                   private val listener: UserLongPollListener) {

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

    /**
     * Parses an update
     * https://vk.com/dev/using_longpoll_2?f=3.+Структура+событий
     */
    private fun parse(update: JsonElement) {
        fun TODO(description: String) = Unit

        val updateArray = update.asJsonArray
        logger.info("{}", updateArray)
        when (updateArray.first().asInt) {
            1 -> TODO("message.flags = flags")
            2 -> TODO("message.flags |= flags")
            3 -> TODO("message.flags &= ~flags")
            4 -> listener.newMessage(client.gson.fromJson(update, Message::class.java))
            5 -> listener.editMessage(client.gson.fromJson(update, Message::class.java))
            6 -> listener.readUpToInbound(client.gson.fromJson(update, MessageReadUpTo::class.java))
            7 -> listener.readUpToOutbound(client.gson.fromJson(update, MessageReadUpTo::class.java))
            8 -> TODO("Friend online")
            9 -> TODO("Friend offline")
            10 -> TODO("peer.flags &= ~flags")
            11 -> TODO("peer.flags = flags")
            12 -> TODO("peer.flags |= flags")
            13 -> TODO("Bulk delete messages")
            14 -> TODO("Bulk restore messages")
            51 -> TODO("Mutlichat info updated")
            52 -> TODO("Additional peer info updated")
            61 -> listener.userIsTyping(updateArray[1].asInt)
            62 -> listener.userIsTyping(client.gson.fromJson(update, UserIsTyping::class.java))
            63 -> listener.usersAreTyping(client.gson.fromJson(update, UsersAreTyping::class.java))
            64 -> TODO("Multiple users are recording a voice message")
            70 -> TODO("User has completed a call")
            80 -> TODO("Message counter is set to a value")
            114 -> TODO("Notification settings updated")
        }
    }

    companion object {
        val logger = createLogger()
    }
}

class UserIsTyping(val userId: Int, val chatId: Int) {
    class UserIsTypingDeserializer : JsonDeserializer<UserIsTyping> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserIsTyping {
            val array = json.asJsonArray
            return UserIsTyping(array[1].asInt, array[2].asInt)
        }
    }
}

class UsersAreTyping(val userIds: List<Int>, val peerId: Int, val timestamp: Int) {
    class UsersAreTypingDeserializer : JsonDeserializer<UsersAreTyping> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UsersAreTyping {
            val array = json.asJsonArray
            val peerId = array[array.size() - 3].asInt
            val timestamp = array[array.size() - 1].asInt
            return UsersAreTyping(array.toList().subList(0, array.size() - 3).map { it.asInt }, peerId, timestamp)
        }
    }
}

class MessageReadUpTo(val peerId: Int, val messageId: Int) {
    class MessageReadUpToDeserializer : JsonDeserializer<MessageReadUpTo> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): MessageReadUpTo {
            val array = json.asJsonArray
            return MessageReadUpTo(array[1].asInt, array[2].asInt)
        }
    }
}