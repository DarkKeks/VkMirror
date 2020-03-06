package ru.darkkeks.vkmirror

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineDatabase
import ru.darkkeks.vkmirror.bot.MirrorBot

class VkMirrorDao(kodein: Kodein) {
    val database: CoroutineDatabase by kodein.instance()

    val bots = database.getCollection<MirrorBot>()
    val chats = database.getCollection<Chat>()
    val messages = database.getCollection<SyncedMessage>()

    val messageLinkMutex = Mutex()
    val messageLink = database.getCollection<MessageIdLink>()

    suspend fun getChatByTelegramId(telegramId: Int): Chat? {
        return chats.findOne(Chat::telegramId eq telegramId)
    }

    suspend fun getChatByVkId(vkId: Int): Chat? {
        return chats.findOne(Chat::peerId eq vkId)
    }

    suspend fun saveMirrorChat(chat: Chat) {
        chats.save(chat)
    }

    suspend fun isSyncedTelegram(chat: Chat, messageId: Long): Boolean {
        return messages.countDocuments(and(
                SyncedMessage::chat eq chat._id,
                SyncedMessage::telegramId eq messageId)) > 0
    }

    suspend fun isSyncedVk(chat: Chat, messageId: Int): Boolean {
        return messages.countDocuments(and(
                SyncedMessage::chat eq chat._id,
                SyncedMessage::vkId eq messageId)) > 0
    }

    suspend fun saveTelegramMessages(chat: Chat, vkId: Int, telegramIds: List<Long>, sender: Int) {
        if (telegramIds.isEmpty()) return
        messages.bulkWrite(telegramIds.map {
            insertOne(SyncedMessage(newId(), chat._id, vkId, it, sender, MessageDirection.VK_TO_TELEGRAM))
        })
    }

    suspend fun saveVkMessages(chat: Chat, vkIds: List<Int>, telegramId: Long) {
        if (vkIds.isEmpty()) return
        messages.bulkWrite(vkIds.map {
            insertOne(SyncedMessage(newId(), chat._id, it, telegramId, -1, MessageDirection.TELEGRAM_TO_VK))
        })
    }

    suspend fun getSyncedMessageByTelegramId(chat: Id<Chat>, telegramMessageId: Long): SyncedMessage? {
        return messages.findOne(and(SyncedMessage::chat eq chat, SyncedMessage::telegramId eq telegramMessageId))
    }

    suspend fun getSyncedMessageByVkId(chat: Id<Chat>, vkMessageId: Int): SyncedMessage? {
        return messages.findOne(and(SyncedMessage::chat eq chat, SyncedMessage::vkId eq vkMessageId))
    }

    suspend fun getBotByVkId(vkId: Int): MirrorBot? {
        return bots.findOne(MirrorBot::vkId eq vkId)
    }

    suspend fun saveMessageIdLink(link: MessageIdLink) {
        messageLinkMutex.withLock<Unit> {
            val oldLink = messageLink.findOne(and(
                    MessageIdLink::chat eq link.chat,
                    MessageIdLink::content eq link.content,
                    MessageIdLink::date eq link.date,
                    MessageIdLink::userId eq link.userId))
            if (oldLink != null) {
                messageLink.save(with (oldLink) {
                    MessageIdLink(_id, chat, content, date, userId, link.messageId)
                })
            } else {
                messageLink.insertOne(link)
            }
        }
    }

    suspend fun getMessageLinks(chat: Id<Chat>, userId: Int, messageId: Long): Map<Int, Long> {
        messageLinkMutex.withLock {
            val originalLink = messageLink.findOne(and(
                    MessageIdLink::chat eq chat,
                    MessageIdLink::userId eq userId,
                    MessageIdLink::messageId eq messageId
            )) ?: return mapOf()

            return messageLink.find(and(
                    MessageIdLink::chat eq chat,
                    MessageIdLink::date eq originalLink.date,
                    MessageIdLink::content eq originalLink.content
            )).toList().associate { it.userId to it.messageId }
        }
    }
}