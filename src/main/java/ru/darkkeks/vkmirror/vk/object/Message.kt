package ru.darkkeks.vkmirror.vk.`object`

import com.google.gson.*
import ru.darkkeks.vkmirror.util.getNullable
import java.lang.reflect.Type

data class Message constructor(
        val messageId: Int,
        val flags: Int,
        val peerId: Int,
        val timestamp: Int,
        val text: String,
        val attachments: JsonObject,
        val attachmentIds: List<Attachment>,
        val additionalInfo: JsonObject,
        val title: String?,
        val from: Int
) {

    class MessageDeserializer : JsonDeserializer<Message> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Message {
            val array = json.asJsonArray

            val messageId = array[1].asInt
            val flags = array[2].asInt

            val peerId = array.getNullable(3)?.asInt ?: 0

            val timestamp = array.getNullable(4)?.asInt ?: 0
            val text = array.getNullable(5)?.asString ?: ""

            val additional = array.getNullable(6)?.asJsonObject ?: JsonObject()
            val attachments = array.getNullable(7)?.asJsonObject ?: JsonObject()

            val from = additional["from"]?.asString?.toInt() ?: peerId
            val title = additional["title"]?.asString

            val attachmentTypes = mutableListOf<Attachment>()

            val infiniteSeries = generateSequence(1) { it + 1 }
            for (position in infiniteSeries) {
                val prefix = "attach$position"
                if (!attachments.has(prefix)) break

                val id = attachments[prefix].asString
                val type: String = attachments["${prefix}_type"].asString
                val enumType = AttachmentType.valueOf(type)

                attachmentTypes.add(Attachment(id, position, enumType))
            }

            return Message(messageId, flags, peerId, timestamp, text, attachments, attachmentTypes, additional, title, from)
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

    class Attachment(val id: String, val pos: Int, val type: AttachmentType)

    enum class AttachmentType {
        PHOTO, VIDEO, AUDIO, DOC, WALL, STICKER, LINK, MONEY
    }
}