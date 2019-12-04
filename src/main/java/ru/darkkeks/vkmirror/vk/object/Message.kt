package ru.darkkeks.vkmirror.vk.`object`

import com.google.gson.*
import java.lang.reflect.Type

data class Message constructor(
        val messageId: Int,
        val flags: Int,
        val peerId: Int,
        val timestamp: Int,
        val text: String?,
        val attachments: JsonObject?,
        val title: String?,
        val from: Int
) {

    class MessageDeserializer : JsonDeserializer<Message> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Message {
            val array = json.asJsonArray

            val messageId = array[1].asInt
            val flags = array[2].asInt
            var peerId = 0
            var timestamp = 0
            var text: String? = null
            var title: String? = null
            var from = 0
            var attachments: JsonObject? = null

            if (array.size() > 3) {
                peerId = array[3].asInt
            }

            if (array.size() > 4) {
                timestamp = array[4].asInt
                text = array[5].asString
            }

            if (array.size() > 6) {
                val additional = array[6].asJsonObject
                if (additional.has("title")) {
                    title = additional["title"].asString
                }
                if (additional.has("from")) {
                    from = additional["from"].asString.toInt()
                }
            }

            if (array.size() > 7) {
                attachments = array[7].asJsonObject
            }

            if (from == 0) {
                from = peerId
            }

            return Message(messageId, flags, peerId, timestamp, text, attachments, title, from)
        }
    }

    object Flags {
        const val UNREAD = 1
        const val OUTBOX = 2
        const val REPLIED = 4
        const val IMPORTANT = 8
        const val CHAT = 16
        const val FRIENDS = 32
        const val SPAM = 64
        const val DELETED = 128
        const val FIXED = 256
        const val MEDIA = 512
        const val HIDDEN = 65536
    }
}