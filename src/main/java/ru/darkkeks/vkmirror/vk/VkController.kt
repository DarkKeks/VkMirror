package ru.darkkeks.vkmirror.vk

import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.exceptions.ApiException
import com.vk.api.sdk.objects.users.Fields
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import ru.darkkeks.vkmirror.tdlib.internal.TdApi
import ru.darkkeks.vkmirror.util.createLogger
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

class VkController(kodein: Kodein) {

    val client: VkApiClient by kodein.instance()
    val actor: UserActor by kodein.instance()

    private val messageAdapter = VkMessageAdapter(kodein, this) {
        client.messages().send(actor)
    }

    suspend fun runLongPoll(handler: UserLongPollListener) {
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
    suspend fun sendMessage(peerId: Int, message: TdApi.Message): List<Int> {
        return coroutineScope {
            messageAdapter.adapt(peerId, message).map {
                async {
                    it.peerId(peerId).randomId(Random.nextInt()).executeSuspending()
                }
            }.map {
                it.await()
            }
        }
    }

    suspend fun markAsRead(peerId: Int, messageId: Int) {
        client.messages().markAsRead(actor).peerId(peerId).startMessageId(messageId).executeSuspending()
    }

    suspend fun downloadConversationImage(peerId: Int): Path? {
        val conversations = MyGetConversationsQuery(client, actor, peerId).executeSuspending()
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
                        .executeSuspending()
                userDetails[0].photo200
            }
        }

        photo ?: return null

        return withContext(Dispatchers.IO) {
            val file = Files.createTempFile("vkmirror-image-", null)
            val channel = Channels.newChannel(photo.openStream())
            val outputStream = FileOutputStream(file.toFile())
            outputStream.channel.transferFrom(channel, 0, Long.MAX_VALUE)

            file
        }
    }

    suspend fun getChannelTitle(peerId: Int): String = when {
        isMultichat(peerId) -> {
            val chat = client.messages().getChatPreview(actor).peerId(peerId).executeSuspending()
            chat.preview.title
        }
        isGroup(peerId) -> {
            val groups = client.groups().getById(actor).groupId((-peerId).toString()).executeSuspending()
            groups[0].name
        }
        else -> {
            val users = client.users().get(actor).userIds(peerId.toString()).executeSuspending()
            "${users[0].firstName} ${users[0].lastName}"
        }
    }

    suspend fun uploadPhotoAttachment(peerId: Int, filePath: String): String? {
        val uploadServer = client.photos().getMessagesUploadServer(actor)
                .peerId(peerId)
                .executeSuspending()

        val photo = client.upload().photoMessage(uploadServer.uploadUrl.toString(), File(filePath))
                .executeSuspending()

        val savedPhoto = client.photos().saveMessagesPhoto(actor, photo.photo)
                .server(photo.server)
                .hash(photo.hash)
                .executeSuspending()
                .first()

        return "photo${savedPhoto.ownerId}_${savedPhoto.id}_${savedPhoto.accessKey}"
    }

    fun uploadVideoAttachment(filePath: String): String? {
        throw UnsupportedOperationException("Video upload is not supported yet")
    }

    companion object {
        val logger = createLogger<VkController>()
    }

}
