package ru.darkkeks.vkmirror

import org.litote.kmongo.Id
import org.litote.kmongo.newId
import ru.darkkeks.vkmirror.vk.ChatType

/**
 * An object describing the relationship between vk peer id (person, multichat or group) and telegram chat (bot or
 * supergroup)
 */
data class Chat(val _id: Id<Chat>, var vkPeerId: Int, val telegramId: Int, val type: ChatType) {

    companion object {
        fun privateChat(vkPeerId: Int, botId: Int): Chat {
            return Chat(newId(), vkPeerId, botId, ChatType.PRIVATE)
        }

        fun groupChat(vkPeerId: Int, telegramChatId: Int): Chat {
            return Chat(newId(), vkPeerId, telegramChatId, ChatType.GROUP)
        }
    }
}