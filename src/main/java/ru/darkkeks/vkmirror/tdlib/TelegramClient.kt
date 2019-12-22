package ru.darkkeks.vkmirror.tdlib


import ru.darkkeks.vkmirror.tdlib.internal.TdApi
import ru.darkkeks.vkmirror.tdlib.internal.TdClient
import ru.darkkeks.vkmirror.util.EventHandler
import ru.darkkeks.vkmirror.util.logger
import java.nio.file.Path

class TelegramClient(credentials: TelegramCredentials) {

    private val updateHandler = EventHandler<TdApi.Object>()

    private val client = TdClient(updateHandler::onMessage, ::handleException)

    private val authHandler = AuthHandler(credentials, client)

    private val options = mutableMapOf<String, Int>()

    private val chats = mutableMapOf<Long, TdApi.Chat>()
    private val supergroups = mutableMapOf<Int, TdApi.Supergroup>()

    init {
        updateHandler.apply {
            addHandler(TdApi.UpdateAuthorizationState::class) {
                authHandler.onAuthorizationStateChange(it)
            }

            addHandler(TdApi.UpdateOption::class) {
                when (val value = it.value) {
                    is TdApi.OptionValueInteger -> options[it.name] = value.value
                }
            }

            addHandler(TdApi.UpdateNewChat::class) {
                chats[it.chat.id] = it.chat
            }

            addHandler(TdApi.UpdateSupergroup::class) {
                supergroups[it.supergroup.id] = it.supergroup
            }
        }
    }

    fun start() {
        client.start()
        authHandler.awaitAuthorization()
    }

    private fun handleException(exception: Throwable) {
        logger.error("Exception in TelegramClient: ", exception)
    }


    val myId
        get() = options["my_id"] ?: -1

    fun getChat(id: Long) = chats[id]

    fun onMessage(handler: (TdApi.UpdateNewMessage) -> Unit) {
        updateHandler.addHandler(TdApi.UpdateNewMessage::class, handler)
    }

    suspend fun setGroupPhoto(chatId: Long, file: Path) {
        val inputFile = TdApi.InputFileLocal(file.toAbsolutePath().toString())
        client.request(TdApi.SetChatPhoto(chatId, inputFile))
    }

    suspend fun getUser(userId: Int): TdApi.User {
        return client.request(TdApi.GetUser(userId)) as TdApi.User
    }

    suspend fun chatAddUser(chatId: Long, userId: Int) {
        // User has to be loaded
        getUser(userId)

        client.request(TdApi.AddChatMember(chatId, userId, 100))
    }

    suspend fun createSupergroup(title: String, description: String): TdApi.Chat {
        val request = TdApi.CreateNewSupergroupChat(title, false, description)
        return when (val response = client.request(request)) {
            is TdApi.Error -> {
                logger.error("Error creating channel {}: {}", title, response)
                throw IllegalStateException("Couldn't create channel")
            }
            else -> response
        } as TdApi.Chat
    }

    suspend fun createPrivateChat(userId: Int): TdApi.Chat {
        val request = TdApi.CreatePrivateChat(userId, false)
        return when (val response = client.request(request)) {
            is TdApi.Error -> {
                logger.error("Error creating private chat {}: {}", userId, response)
                throw java.lang.IllegalStateException("Couldn't create private chat")
            }
            else -> response
        } as TdApi.Chat
    }

    suspend fun searchPublicUsername(username: String): TdApi.Chat {
        return client.request(TdApi.SearchPublicChat(username)) as TdApi.Chat
    }

    suspend fun sendMessage(chatId: Long, text: String): TdApi.Message {
        val message = TdApi.InputMessageText(TdApi.FormattedText(text, null), false, true)
        val request = TdApi.SendMessage(chatId, 0, true, true, null, message)
        return client.request(request) as TdApi.Message
    }

    suspend fun groupById(supergroupId: Int): TdApi.Chat? {
        val request = TdApi.CreateSupergroupChat(supergroupId, false)
        return when (val response = client.request(request)) {
            is TdApi.Error -> {
                logger.error("Error getting group by id {}: {}", supergroupId, response)
                return null
            }
            else -> response
        } as TdApi.Chat
    }

    suspend fun deleteMessage(chatId: Long, revoke: Boolean, vararg messageIds: Long) {
        client.request(TdApi.DeleteMessages(chatId, messageIds, revoke))
    }


    companion object {
        val logger = logger()
    }
}