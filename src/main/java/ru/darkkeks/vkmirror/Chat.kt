package ru.darkkeks.vkmirror

import ru.darkkeks.vkmirror.vk.ChatType

/**
 * An object describing the relationship between vk peer id (person, multichat or group) and telegram chat (bot or
 * supergroup)
 */
data class Chat(var id: Int, val vkPeerId: Int, val telegramId: Int, val type: ChatType) {

    // TODO JvmStatic is not needed
    companion object {
        @JvmStatic
        fun privateChat(vkPeerId: Int, botId: Int): Chat {
            return Chat(-1, vkPeerId, botId, ChatType.PRIVATE)
        }

        @JvmStatic
        fun groupChat(vkPeerId: Int, telegramChatId: Int): Chat {
            return Chat(-1, vkPeerId, telegramChatId, ChatType.GROUP)
        }
    }

}