package ru.darkkeks.vkmirror

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
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
        telegram.onMessage {
            if (it.message.senderUserId == telegram.myId) {
                scope.launch {
                    handleTelegramMessage(it.message)
                }
            }
        }

        telegram.start()
        vk.runLongPoll(VkListener())
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
                        val groupId = chat.telegramId.toInt()
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
        }

        override fun readUpToOutbound(event: MessageReadUpTo) {
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