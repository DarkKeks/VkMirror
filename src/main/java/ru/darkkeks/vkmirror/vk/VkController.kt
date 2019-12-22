package ru.darkkeks.vkmirror.vk

import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.exceptions.ApiException
import com.vk.api.sdk.objects.users.Fields
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import ru.darkkeks.vkmirror.tdlib.internal.TdApi
import ru.darkkeks.vkmirror.util.logger
import ru.darkkeks.vkmirror.vk.`object`.Message
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

class VkController(kodein: Kodein) {

    val client: VkApiClient by kodein.instance()
    val actor: UserActor by kodein.instance()

    private val myId: Int

    private val messageAdapter = VkMessageAdapter {
        client.messages().send(actor)
    }

    init {
        try {
            myId = client.users().get(actor).execute()[0].id
        } catch (e: Exception) {
            throw IllegalStateException("Can't get my id", e)
        }
    }

    fun runLongPoll(handler: (Message) -> Unit) {
        val longPoll = UserLongPoll(client, actor, handler)
        try {
            longPoll.run()
        } catch (e: ApiException) {
            logger.error("Long polling exception", e)
        }
    }

    /**
     * @return list of produced messages
     */
    fun sendMessage(peerId: Int, message: TdApi.Message): List<Int> {
        return messageAdapter.adapt(message).map {
            it.peerId(peerId).randomId(Random.nextInt()).execute()
        }
    }

    fun downloadConversationImage(peerId: Int): Path? {
        val conversations = MyGetConversationsQuery(client, actor, peerId).execute()
        val conversation = conversations.items[0]

        val photo: URL? = when {
            isMultichat(peerId) -> {
                conversation.chatSettings?.photos?.photo200
            }
            isGroup(peerId) -> {
                conversations.groups[0].photo200
            }
            else -> {
                val user = conversations.profiles[0]
                val userDetails = client.users().get(actor)
                        .userIds(user.id.toString())
                        .fields(Fields.PHOTO_200)
                        .execute()
                userDetails[0].photo200
            }
        }

        if (photo != null) {
            val file = Files.createTempFile("vkmirror-image-", null)
            val channel = Channels.newChannel(photo.openStream())
            val outputStream = FileOutputStream(file.toFile())
            outputStream.channel.transferFrom(channel, 0, Long.MAX_VALUE)

            return file
        }

        return null
    }

    fun getChannelTitle(peerId: Int): String = when {
        isMultichat(peerId) -> {
            val chat = client.messages().getChatPreview(actor).peerId(peerId).execute()
            chat.preview.title
        }
        isGroup(peerId) -> {
            val groups = client.groups().getById(actor).groupId((-peerId).toString()).execute()
            groups[0].name
        }
        else -> {
            val users = client.users().get(actor).userIds(peerId.toString()).execute()
            "${users[0].firstName} ${users[0].lastName}"
        }
    }

    companion object {
        val logger = logger()
    }

}
