package ru.darkkeks.vkmirror

import org.litote.kmongo.Id
import org.litote.kmongo.newId
import ru.darkkeks.vkmirror.vk.ChatType

/**
 * An object describing the relationship between vk peer id (person, multichat or group) and telegram chat (bot or
 * supergroup)
 */
data class Chat(val _id: Id<Chat>, var peerId: Int, val telegramId: Int, val type: ChatType) {

    companion object {
        fun privateChat(peerId: Int, botId: Int): Chat {
            return Chat(newId(), peerId, botId, ChatType.PRIVATE)
        }

        fun groupChat(peerId: Int, telegramChatId: Int): Chat {
            return Chat(newId(), peerId, telegramChatId, ChatType.GROUP)
        }
    }
}