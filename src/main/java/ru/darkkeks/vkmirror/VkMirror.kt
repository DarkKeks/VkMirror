package ru.darkkeks.vkmirror

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import org.litote.kmongo.newId
import ru.darkkeks.vkmirror.bot.MirrorBot
import ru.darkkeks.vkmirror.bot.START
import ru.darkkeks.vkmirror.tdlib.TelegramClient
import ru.darkkeks.vkmirror.tdlib.botTelegramCredentials
import ru.darkkeks.vkmirror.tdlib.internal.TdApi
import ru.darkkeks.vkmirror.util.createLogger
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
        telegram.subscribe<TdApi.UpdateNewMessage> {
            logger.info("Incoming message from chat ${it.message.chatId} with id ${it.message.id}")
            val chat = telegram.getChat(it.message.chatId) ?: return@subscribe
            scope.launch {
                val id = when (val type = chat.type) {
                    is TdApi.ChatTypePrivate -> type.userId
                    is TdApi.ChatTypeSupergroup -> type.supergroupId
                    else -> return@launch
                }
                linkTelegramId(id, telegram.myId, it.message)
            }
            scope.launch {
                if (it.message.senderUserId == telegram.myId) {
                    handleTelegramMessage(it.message)
                }
            }
        }

        telegram.subscribe<TdApi.UpdateChatReadInbox> {
            logger.info("Received UpdateChatReadInbox in chat ${it.chatId} with last message ${it.lastReadInboxMessageId}")
            scope.launch {
                val chat = telegramChatIdToMirrorChat(it.chatId) ?: return@launch
                val message = telegram.getMessage(it.chatId, it.lastReadInboxMessageId)
                logger.info("UpdateChatReadInbox message sender is ${message.senderUserId}")
                val messageIds = dao.getMessageLinks(chat._id, telegram.myId, it.lastReadInboxMessageId)
                logger.info("UpdateChatReadInbox known message ids are $messageIds")
                val idFromBotPerspective = messageIds[message.senderUserId] ?: return@launch
                logger.info("UpdateChatReadInbox id from bots perspective is $idFromBotPerspective")
                val vkMessageId = dao.getSyncedMessage(chat._id, idFromBotPerspective)?.vkId ?: return@launch
                logger.info("UpdateChatReadInbox sending vk api request with peerId ${chat.vkPeerId} and message id $vkMessageId")
                vk.markAsRead(chat.vkPeerId, vkMessageId)
            }
        }

        telegram.start()
        vk.runLongPoll(VkListener())
    }

    private suspend fun linkTelegramId(chatOrSupergroupId: Int, userId: Int, message: TdApi.Message) {
        val chat = dao.getChatByTelegramId(chatOrSupergroupId) ?: return
        logger.info("Adding message-id link for chat ${chat.vkPeerId} for user $userId, messageId ${message.id}")
        val link = MessageIdLink(newId(), chat._id, message.content.toString(), message.date.toLong(), userId, message.id)
        dao.saveMessageIdLink(link)
    }

    private suspend fun telegramChatIdToMirrorChat(chatId: Long): Chat? {
        val chat = telegram.getChat(chatId) ?: return null

        return when (val type = chat.type) {
            is TdApi.ChatTypePrivate -> dao.getChatByTelegramId(type.userId)
            is TdApi.ChatTypeSupergroup -> dao.getChatByTelegramId(type.supergroupId)
            else -> null
        }
    }

    private suspend fun handleTelegramMessage(message: TdApi.Message) {
        val mirrorChat = telegramChatIdToMirrorChat(message.chatId) ?: return

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

    private suspend fun sendVkMessage(client: TelegramClient, chat: Chat, chatId: Long, message: Message) {
        mutex.withLock {
            if (!dao.isSyncedVk(chat, message.messageId)) {
                logger.info("Sending to chat $chatId")
                val tgMessage = client.sendMessage(chatId, message.text ?: "")
                logger.info("Message sent $tgMessage")
                linkTelegramId(chat.telegramId, client.myId, tgMessage)
                dao.saveTelegramMessages(chat, message.messageId, listOf(tgMessage.id))
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
                // TODO Add logging in places like this ?
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
        return clients.computeIfAbsent(bot) {
            TelegramClient(botTelegramCredentials(API_ID, API_HASH, bot.token)).also { client ->
                client.subscribe<TdApi.UpdateNewMessage> { update ->
                    val message = update.message
                    logger.info("Bot incoming message from chat ${message.chatId} with id ${message.id}")

                    scope.launch {
                        val chat = client.getChat(message.chatId) ?: return@launch
                        val id = when (val type = chat.type) {
                            is TdApi.ChatTypePrivate -> client.myId
                            is TdApi.ChatTypeSupergroup -> type.supergroupId
                            else -> return@launch
                        }
                        linkTelegramId(id, client.myId, message)
                    }

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
            }
        }.apply {
            start()
        }
    }

    inner class VkListener : UserLongPollListener {
        override fun newMessage(message: Message) {
            scope.launch {
                logger.info("New message from ${message.from} -> id=${message.messageId} \"${message.text}\"")

                val chat = getTelegramChat(message.peerId)

                if (chat == null || dao.isSyncedVk(chat, message.messageId)) {
                    return@launch
                }

                val client = when (message.flags and Message.Flags.OUTBOX > 0) {
                    true -> telegram
                    false -> {
                        val bot = dao.getBotByVkId(message.from) ?: return@launch
                        createBotClient(bot)
                    }
                }

                when (chat.type) {
                    ChatType.PRIVATE -> {
                        val recipient = if (client == telegram) chat.telegramId else telegram.myId
                        sendVkMessage(client, chat, recipient.toLong(), message)
                    }
                    ChatType.GROUP -> {
                        val groupId = chat.telegramId
                        val group = client.groupById(groupId)

                        if (group == null) {
                            val newGroup = telegram.groupById(groupId)
                                    ?: throw IllegalStateException("User left group") // TODO: Handle leaving group
                            telegram.chatAddUser(newGroup.id, client.myId)

                            val joinedGroup = client.groupById(groupId)
                                    ?: throw IllegalStateException("Failed to add bot to group") // TODO: Handle add failure

                            sendVkMessage(client, chat, joinedGroup.id, message)
                        } else {
                            sendVkMessage(client, chat, group.id, message)
                        }
                    }
                }
            }
        }

        override fun editMessage(editedMessage: Message) {

        }

        override fun readUpToInbound(event: MessageReadUpTo) {
            // https://quire.io/w/HSE-24/189#comment-OENM7zHkvQeHWdfmQktRfva~
        }

        override fun readUpToOutbound(event: MessageReadUpTo) {
            // https://quire.io/w/HSE-24/189#comment-OENM7zHkvQeHWdfmQktRfva~
        }

        override fun userIsTyping(userId: Int) {
            scope.launch {
                val chat = getTelegramChat(userId) ?: return@launch
                val bot = dao.getBotByVkId(userId)
                if (bot == null) {
                    logger.info("Received typing status for user without a bot")
                } else {
                    val client = createBotClient(bot)
                    client.sendChatAction(telegram.myId.toLong(), TdApi.ChatActionTyping())
                }
            }
        }

        override fun userIsTyping(event: UserIsTyping) {
            scope.launch {
                val peerId = if (isGroup(event.chatId)) event.chatId else event.chatId + MULTICHAT_BASE
                val chat = getTelegramChat(peerId) ?: return@launch
                val bot = dao.getBotByVkId(event.userId)
                if (bot == null) {
                    logger.info("Received typing status for user without a bot")
                } else {
                    val client = createBotClient(bot)
                    val group = client.groupById(chat.telegramId) ?: throw IllegalStateException()
                    client.sendChatAction(group.id, TdApi.ChatActionTyping())
                }
            }
        }

        override fun usersAreTyping(event: UsersAreTyping) {
            event.userIds.forEach { userId ->
                userIsTyping(UserIsTyping(userId, event.peerId))
            }
        }
    }

    companion object {
        val logger = createLogger()
    }
}