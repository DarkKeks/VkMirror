package ru.darkkeks.vkmirror

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import org.litote.kmongo.Id
import org.litote.kmongo.newId
import ru.darkkeks.vkmirror.bot.MirrorBot
import ru.darkkeks.vkmirror.bot.START
import ru.darkkeks.vkmirror.tdlib.TelegramClient
import ru.darkkeks.vkmirror.tdlib.botTelegramCredentials
import ru.darkkeks.vkmirror.tdlib.internal.TdApi
import ru.darkkeks.vkmirror.util.createLogger
import ru.darkkeks.vkmirror.vk.*
import ru.darkkeks.vkmirror.vk.`object`.Message
import java.util.concurrent.ConcurrentHashMap

class VkMirror(kodein: Kodein) {

    private val vk: VkController by kodein.instance()
    private val telegram: TelegramClient by kodein.instance()

    private val dao = VkMirrorDao(kodein)

    private val clients = ConcurrentHashMap<MirrorBot, TelegramClient>()

    private val chatLocks = ConcurrentHashMap<Id<Chat>, Mutex>()

    private val scope = CoroutineScope(SupervisorJob())

    suspend fun start() {
        telegram.subscribe<TdApi.UpdateNewMessage> {
            handleTelegramMessage(it.message)
        }

        telegram.subscribe<TdApi.UpdateMessageSendSucceeded> {
            handleTelegramMessage(it.message)
        }

        telegram.subscribe<TdApi.UpdateChatReadInbox> {
            logger.info("Received UpdateChatReadInbox in chat ${it.chatId} with last message ${it.lastReadInboxMessageId}")
            scope.launch {
                val chat = telegramChatIdToMirrorChat(it.chatId) ?: return@launch
                val message = telegram.getMessage(it.chatId, it.lastReadInboxMessageId)
                val messageIds = dao.getMessageLinks(chat._id, telegram.myId, it.lastReadInboxMessageId)
                val idFromBotPerspective = messageIds[message.senderUserId] ?: return@launch
                val vkMessageId = dao.getSyncedMessageByTelegramId(chat._id, idFromBotPerspective)?.vkId
                        ?: return@launch
                logger.info("Marking vk messages up to $vkMessageId in peer ${chat.peerId} as read")
                vk.markAsRead(chat.peerId, vkMessageId)
            }
        }

        telegram.start()
        vk.runLongPoll(VkListener())
    }

    private fun handleTelegramMessage(message: TdApi.Message) {
        if (message.sendingState != null) return

        logger.info("Incoming message from chat ${message.chatId} with id ${message.id}")
        scope.launch {
            val mirrorChat = telegramChatIdToMirrorChat(message.chatId) ?: return@launch

            launch {
                addMessageIdLink(mirrorChat, telegram.myId, message)
            }

            if (message.senderUserId == telegram.myId) {
                logger.info("Received message from myself id=${message.id} \"${message.content}\"")
                if (!dao.isSyncedTelegram(mirrorChat, message.id)) {
                    val vkMessages = vk.adaptMessage(mirrorChat.peerId, message)
                    getChatLock(mirrorChat).withLock {
                        if (!dao.isSyncedTelegram(mirrorChat, message.id)) {
                            logger.info("Message {} is not synced", message.id)

                            val ids = vk.sendMessages(vkMessages)
                            logger.info("Produced vk messages: {}", ids.toString())

                            dao.saveVkMessages(mirrorChat, ids, message.id)
                        }
                    }
                }
            }
        }
    }

    private suspend fun addMessageIdLink(chat: Chat, userId: Int, message: TdApi.Message) {
        logger.info("Adding message-id link for chat ${chat.peerId} for user $userId, messageId ${message.id}")
        val link = MessageIdLink(newId(), chat._id, message.content.toString(), message.date.toLong(), userId, message.id)
        dao.saveMessageIdLink(link)
    }

    private suspend fun telegramChatIdToMirrorChat(chatId: Long, client: TelegramClient = telegram): Chat? {
        val chat = telegram.getChat(chatId) ?: return null

        val id = when (val type = chat.type) {
            is TdApi.ChatTypePrivate -> {
                if (client.myId != telegram.myId) {
                    client.myId
                } else {
                    type.userId
                }
            }
            is TdApi.ChatTypeSupergroup -> type.supergroupId
            else -> return null
        }

        return dao.getChatByTelegramId(id)
    }

    private suspend fun mirrorChatForPeerId(peerId: Int): Chat? {
        return dao.getChatByVkId(peerId) ?: when {
            isPrivateChat(peerId) -> {
                // TODO Add logging in places like this ?
                val bot = dao.getBotByVkId(peerId) ?: return null

                val chat = telegram.searchPublicUsername(bot.username)
                telegram.sendMessage(chat.id, START)

                // Just so we delete /start message
                val client = createBotClient(bot)

                Chat.privateChat(peerId, client.myId)
            }
            isMultichat(peerId) -> {
                val tgChat = createTelegramGroup(peerId)
                val supergroup = tgChat.type as TdApi.ChatTypeSupergroup

                Chat.groupChat(peerId, supergroup.supergroupId)
            }
            else -> throw UnsupportedOperationException("Group chats are not supported yet")
        }.also {
            dao.saveMirrorChat(it)
        }
    }

    private suspend fun createTelegramGroup(peerId: Int): TdApi.Chat {
        val chat = telegram.createSupergroup(vk.getChatTitle(peerId), getChatUrl(peerId))


        coroutineScope {
            launch {
                val photo = vk.downloadConversationImage(peerId)
                if (photo != null) {
                    telegram.setGroupPhoto(chat.id, photo)
                }
            }

            vk.getMultichatMembers(peerId).mapNotNull {
                dao.getBotByVkId(it)
            }.forEach {
                launch {
                    telegram.chatAddUser(chat.id, createBotClient(it).myId)
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
                        val chat = telegramChatIdToMirrorChat(message.chatId, client = client) ?: return@launch
                        addMessageIdLink(chat, client.myId, message)
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

    private fun getChatLock(chat: Chat): Mutex {
        return chatLocks.computeIfAbsent(chat._id) { Mutex() }
    }

    private suspend fun sendTypingAction(vkUserId: Int, chatId: suspend (TelegramClient) -> Long) {
        val bot = dao.getBotByVkId(vkUserId)
        if (bot == null) {
            logger.info("Received typing status for user without a bot")
        } else {
            val client = createBotClient(bot)
            client.sendChatAction(chatId(client), TdApi.ChatActionTyping())
        }
    }

    inner class VkListener : UserLongPollListener {

        private suspend fun sendVkMessage(client: TelegramClient, chat: Chat, chatId: Long, message: Message) {
            logger.info("Sending to chat $chatId")

            val messages = vk.adaptMessage(message)
            messages.forEach { inputContent ->
                val tgMessage = client.sendMessage(chatId, inputContent)
                logger.info("Message sent $tgMessage")
                addMessageIdLink(chat, client.myId, tgMessage)
                dao.saveTelegramMessages(chat, message.messageId, listOf(tgMessage.id), client.myId)
            }
        }

        override fun newMessage(message: Message) {
            scope.launch {
                logger.info("New message from ${message.from} -> id=${message.messageId} \"${message.text}\"")

                val chat = mirrorChatForPeerId(message.peerId) ?: return@launch

                getChatLock(chat).withLock {
                    if (dao.isSyncedVk(chat, message.messageId)) return@launch

                    val client = if (message.flags and Message.Flags.OUTBOX > 0) {
                        telegram
                    } else {
                        val bot = dao.getBotByVkId(message.from) ?: return@launch
                        createBotClient(bot)
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
        }

        override fun editMessage(editedMessage: Message) {

        }

        override fun readUpToInbound(event: MessageReadUpTo) {
            scope.launch {
                val chat = mirrorChatForPeerId(event.peerId) ?: return@launch
                getChatLock(chat).withLock {
                    val message = dao.getSyncedMessageByVkId(chat._id, event.messageId) ?: return@launch
                    val messageIds = dao.getMessageLinks(chat._id, message.sender, message.telegramId)
                    val messageIdFromByPerspective = messageIds[telegram.myId] ?: return@launch
                    val telegramChatId = when (chat.type) {
                        ChatType.PRIVATE -> chat.telegramId.toLong()
                        ChatType.GROUP -> {
                            val supergroup = telegram.groupById(chat.telegramId) ?: return@launch
                            supergroup.id
                        }
                    }
                    telegram.readMessages(telegramChatId, messageIdFromByPerspective, forceRead = true)
                }
            }
        }

        override fun readUpToOutbound(event: MessageReadUpTo) {
            // Управлять прочтением собщений ботом невозможно
        }

        override fun userIsTyping(userId: Int) {
            scope.launch {
                sendTypingAction(userId) {
                    telegram.myId.toLong()
                }
            }
        }

        override fun userIsTyping(event: UserIsTyping) {
            scope.launch {
                val peerId = if (isGroup(event.chatId)) event.chatId else event.chatId + MULTICHAT_BASE
                val chat = mirrorChatForPeerId(peerId) ?: return@launch

                sendTypingAction(event.userId) { client ->
                    client.groupById(chat.telegramId)?.id ?: throw IllegalStateException("Bot is not in group")
                }
            }
        }

        override fun usersAreTyping(event: UsersAreTyping) {
            scope.launch {
                val peerId = event.peerId
                val chat = mirrorChatForPeerId(peerId) ?: return@launch

                event.userIds.forEach { userId ->
                    launch {
                        sendTypingAction(userId) { client ->
                            client.groupById(chat.telegramId)?.id ?: throw IllegalStateException("Bot is not in group")
                        }
                    }
                }
            }
        }
    }

    companion object {
        val logger = createLogger<VkMirror>()
    }
}