package ru.darkkeks.vkmirror

import org.litote.kmongo.Id

data class MessageIdLink(val _id: Id<MessageIdLink>,
                         val chat: Id<Chat>,
                         val content: String,
                         val date: Long,
                         val userId: Int,
                         val messageId: Long)