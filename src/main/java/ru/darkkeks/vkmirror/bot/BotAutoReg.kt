package ru.darkkeks.vkmirror.bot

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import ru.darkkeks.vkmirror.tdlib.TelegramClient
import ru.darkkeks.vkmirror.tdlib.internal.TdApi


class BotAutoReg(kodein: Kodein) {

    val client: TelegramClient by kodein.instance()

    private val actions = Channel<BotFatherAction<*>>()
    private val responses = Channel<TdApi.Message>()

    private lateinit var botFatherChat: TdApi.Chat

    suspend fun start() {
        client.subscribe<TdApi.UpdateNewMessage> {
            val message = it.message
            if (message.chatId == botFatherChat.id && message.senderUserId != client.myId) {
                println("Received message $message")
                GlobalScope.launch {
                    // FIXME
                    responses.send(message)
                }
            }
        }

        botFatherChat = client.searchPublicUsername(BOT_FATHER)

        // Reset state if it is messed up for some reason
        executeCommand(START)

        actionLoop()
    }

    private suspend fun actionLoop() {
        while (true) {
            val action = actions.receive()
            action.execute()
        }
    }

    suspend fun executeCommand(command: String): String {
        sendMessage(command)
        val response = responses.receive()
        val content = response.content
        return (content as TdApi.MessageText).text.text
    }

    suspend fun sendMessage(message: String) = client.sendMessage(botFatherChat.id, message)

    /**
     * Creates new bot with specified name and username
     * @param username Bot username (without @), has to not be used
     * @param name Bot name
     * @return registered bot token
     */
    suspend fun register(username: String, name: String): String {
        val action = RegisterAction(this, username, name)
        actions.send(action)
        return action.callback.await()
    }

    /**
     * Sets bot about text
     * @param username Bot username (with @)
     * @param about New about text
     */
    suspend fun setAbout(username: String, about: String) {
        val action = SetAboutAction(this, username, about)
        actions.send(action)
        action.callback.await()
    }

    /**
     * Sets bot name
     * @param username Bot username (with @)
     * @param name New bot name
     */
    suspend fun setName(username: String, name: String) {
        val action = SetNameAction(this, username, name)
        actions.send(action)
        action.callback.await()
    }

    // TODO BotAutoReg::setAvatar
    suspend fun setAvatar(username: String, avatar: Any) {
        throw UnsupportedOperationException()
    }
}
