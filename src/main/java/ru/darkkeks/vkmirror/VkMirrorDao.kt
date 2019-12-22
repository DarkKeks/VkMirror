package ru.darkkeks.vkmirror

import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import org.litote.kmongo.and
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import org.litote.kmongo.insertOne
import org.litote.kmongo.newId
import ru.darkkeks.vkmirror.bot.MirrorBot

class VkMirrorDao(kodein: Kodein) {
    val database: CoroutineDatabase by kodein.instance()

    val bots = database.getCollection<MirrorBot>()
    val chats = database.getCollection<Chat>()
    val messages = database.getCollection<SyncedMessage>()

    suspend fun getChatByTelegramId(telegramId: Int): Chat? {
        return chats.findOne(Chat::telegramId eq telegramId)
    }

    suspend fun getChatByVkId(vkId: Int): Chat? {
        return chats.findOne(Chat::vkPeerId eq vkId)
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

    suspend fun saveTelegramMessages(chat: Chat, vkId: Int, telegramIds: List<Long>) {
        messages.bulkWrite(telegramIds.map {
            insertOne(SyncedMessage(newId(), chat._id, vkId, it))
        })
    }

    suspend fun saveVkMessages(chat: Chat, vkIds: List<Int>, telegramId: Long) {
        messages.bulkWrite(vkIds.map {
            insertOne(SyncedMessage(newId(), chat._id, it, telegramId))
        })
    }

    suspend fun getBotByVkId(vkId: Int): MirrorBot? {
        return bots.findOne(MirrorBot::vkId eq vkId)
    }
}