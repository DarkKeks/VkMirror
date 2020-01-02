package ru.darkkeks.vkmirror

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import ru.darkkeks.vkmirror.bot.MirrorBot
import ru.darkkeks.vkmirror.bot.START
import ru.darkkeks.vkmirror.tdlib.TelegramClient
import ru.darkkeks.vkmirror.tdlib.botTelegramCredentials
import ru.darkkeks.vkmirror.tdlib.internal.TdApi
import ru.darkkeks.vkmirror.util.logger
import ru.darkkeks.vkmirror.vk.*
import ru.darkkeks.vkmirror.vk.`object`.Message

class VkMirror(kodein: Kodein) {

    private val vk: VkController by kodein.instance()
    private val telegram: TelegramClient by kodein.instance()

    private val dao = VkMirrorDao(kodein)

    private val clients = mutableMapOf<MirrorBot, TelegramClient>()

    private val mutex = Mutex()

    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun start() {
        telegram.onMessage {
            if (it.message.senderUserId == telegram.myId) {
                scope.launch {
                    handleTelegramMessage(it.message)
                }
            }
        }

        telegram.start()

        vk.runLongPoll {
            GlobalScope.launch { // FIXME
                handleVkMessage(it)
            }
        }
    }

    private suspend fun handleTelegramMessage(message: TdApi.Message) {
        val chat = telegram.getChat(message.chatId) ?: return

        val mirrorChat = when (val type = chat.type) {
            is TdApi.ChatTypePrivate -> dao.getChatByTelegramId(type.userId)
            is TdApi.ChatTypeSupergroup -> dao.getChatByTelegramId(type.supergroupId)
            else -> return
        } ?: return

        logger.info("Received message from myself id=${message.id} \"${message.content}\"")
        mutex.withLock {
            if (!dao.isSyncedTelegram(mirrorChat, message.id)) {
                logger.info("Message {} is not synced", message.id)

                val ids = vk.sendMessage(mirrorChat.vkPeerId, message)
                logger.info("Produced vk messages: {}", ids.toString())

                dao.saveVkMessages(mirrorChat, ids, message.id)
            }
        }
    }

    private suspend fun handleVkMessage(message: Message) {
        logger.info("New message from ${message.from} -> id=${message.messageId} \"${message.text}\"")

        val chat = getTelegramChat(message.peerId) ?: return

        if (dao.isSyncedVk(chat, message.messageId)) {
            return
        }

        val client = when (message.flags and Message.Flags.OUTBOX > 0) {
            true -> telegram
            else -> {
                val bot = dao.getBotByVkId(message.from) ?: return
                createBotClient(bot)
            }
        }

        when (chat.type) {
            ChatType.PRIVATE -> {
                val recipient = if (client == telegram) chat.telegramId else telegram.myId
                sendVkMessage(client, chat, recipient.toLong(), message)
            }
            ChatType.GROUP -> {
                val group = client.groupById(chat.telegramId)

                if (group == null) {
                    val newGroup = telegram.groupById(chat.telegramId)
                            ?: throw IllegalStateException("User left group") // TODO: Handle leaving group
                    telegram.chatAddUser(newGroup.id, client.myId)

                    val joinedGroup = client.groupById(chat.telegramId)
                            ?: throw IllegalStateException("Failed to add bot to group") // TODO: Handle add failure

                    sendVkMessage(client, chat, joinedGroup.id, message)
                } else {
                    sendVkMessage(client, chat, group.id, message)
                }
            }
        }
    }

    private suspend fun sendVkMessage(client: TelegramClient, chat: Chat, chatId: Long, message: Message) {
        mutex.withLock {
            if (!dao.isSyncedVk(chat, message.messageId)) {
                logger.info("Sending to chat $chatId")
                val tgMessage = client.sendMessage(chatId, message.text ?: "")
                logger.info("Message sent $tgMessage")

                dao.saveTelegramMessages(chat, message.messageId, listOf(tgMessage.id))
                logger.info("Synced message $tgMessage!")
            }
        }
    }

    private suspend fun getTelegramChat(vkPeerId: Int): Chat? {
        val mirrorChat = dao.getChatByVkId(vkPeerId)

        if (mirrorChat != null) {
            return mirrorChat
        }

        val chat = when {
            isMultichat(vkPeerId) -> {
                val tgChat = createTelegramGroup(vkPeerId, vk.getChannelTitle(vkPeerId))
                val supergroup = tgChat.type as TdApi.ChatTypeSupergroup

                Chat.groupChat(vkPeerId, supergroup.supergroupId)
            }
            isPrivateChat(vkPeerId) -> {
                val bot = dao.getBotByVkId(vkPeerId) ?: return null

                val chat = telegram.searchPublicUsername(bot.username)
                telegram.sendMessage(chat.id, START)

                // Just so we delete /start message
                val client = createBotClient(bot)

                Chat.privateChat(vkPeerId, client.myId)
            }
            else -> throw UnsupportedOperationException("Group chats are not supported yet")
        }

        dao.saveMirrorChat(chat)
        return chat
    }

    private suspend fun createTelegramGroup(vkPeerId: Int, title: String): TdApi.Chat {
        val chat = telegram.createSupergroup(title, getChatUrl(vkPeerId))

        if (chat.type is TdApi.ChatTypeSupergroup) {
            // TODO Should join every person that is already bound to a bot;

            scope.launch {
                val photo = vk.downloadConversationImage(vkPeerId)
                if (photo != null) {
                    telegram.setGroupPhoto(chat.id, photo)
                }
            }
        }

        return chat
    }

    private suspend fun createBotClient(bot: MirrorBot): TelegramClient {
        val client = clients.computeIfAbsent(bot) {
            val client = TelegramClient(botTelegramCredentials(API_ID, API_HASH, bot.token))

            client.onMessage {
                val message = it.message
                when (val content = message.content) {
                    is TdApi.MessageText -> {
                        if (content.text.text == "/start") {
                            scope.launch {
                                // TODO Log errors ?
                                client.deleteMessage(message.chatId, true, message.id)
                            }
                        }
                    }
                }
            }

            client
        }

        client.start()
        return client
    }


    companion object {
        val logger = logger()
    }
}

