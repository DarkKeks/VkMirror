package ru.darkkeks.vkmirror

import org.litote.kmongo.Id

data class SyncedMessage(val _id: Id<SyncedMessage>,
                         val chat: Id<Chat>,
                         val vkId: Int,
                         val telegramId: Long)