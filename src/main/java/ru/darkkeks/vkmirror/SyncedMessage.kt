package ru.darkkeks.vkmirror

import org.litote.kmongo.Id

data class SyncedMessage(val _id: Id<SyncedMessage>,
                         val chat: Id<Chat>,
                         val vkId: Int,
                         val telegramId: Long,
                         val sender: Int,
                         val direction: MessageDirection,
                         val isRead: Boolean = false)

enum class MessageDirection {
    VK_TO_TELEGRAM,
    TELEGRAM_TO_VK
}