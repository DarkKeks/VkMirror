package ru.darkkeks.vkmirror.vk

import com.vk.api.sdk.queries.messages.MessagesSendQuery
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import ru.darkkeks.vkmirror.tdlib.TelegramClient
import ru.darkkeks.vkmirror.tdlib.internal.TdApi
import ru.darkkeks.vkmirror.util.buildList
import ru.darkkeks.vkmirror.util.createLogger

class VkMessageAdapter(kodein: Kodein,
                       val vk: VkController,
                       val queryProvider: () -> MessagesSendQuery) {

    val telegram: TelegramClient by kodein.instance()

    suspend fun adapt(peerId: Int, message: TdApi.Message) = buildList<MessagesSendQuery> {
        when (val content = message.content) {
            is TdApi.MessageText -> {
                val text = content.text.text
                require(text.isNotBlank()) { "Message text cannot be blank" }
                val query = queryProvider().message(text)
                if (content.webPage == null) {
                    query.dontParseLinks(true)
                }
                add(query)
            }
            is TdApi.MessageAnimation -> {
                val text: String = content.caption.text
                val query = queryProvider().message(text).dontParseLinks(true)

                val animation: TdApi.Animation = content.animation
                val file = telegram.downloadFile(animation.animation)
                logger.info("Downloaded file: {}", file)
                val attachment = if (!file.local.isDownloadingCompleted) {
                    logger.error("Failed to adapt animation message, file isn't downloaded")
                    null
                } else {
                    when (animation.mimeType) {
                        "image/gif" -> vk.uploadPhotoAttachment(peerId, file.local.path)
                        "video/mp4" -> vk.uploadVideoAttachment(file.local.path)
                        else -> null
                    }
                }

                if (attachment != null) {
                    query.attachment(attachment)
                }

                if (attachment != null || text.isNotBlank()) {
                    add(query)
                }
            }
            is TdApi.MessageAudio -> Unit
            is TdApi.MessageDocument -> {

            }
            is TdApi.MessagePhoto -> {
                val text: String = content.caption.text
                val query = queryProvider().dontParseLinks(true)

                if (text.isNotBlank()) {
                    query.message(text)
                }

                val photo = content.photo
                val attachment = if (photo.sizes.isNotEmpty()) {
                    val best = photo.sizes.maxBy { it.height * it.width } ?: throw IllegalStateException()
                    val file = telegram.downloadFile(best.photo)

                    if (!file.local.isDownloadingCompleted) {
                        logger.error("Failed to adapt photo message, file isn't downloaded")
                        null
                    } else {
                        vk.uploadPhotoAttachment(peerId, file.local.path)
                    }
                } else {
                    null
                }

                if (attachment != null) {
                    query.attachment(attachment)
                }

                if (attachment != null || text.isNotBlank()) {
                    add(query)
                }
            }
            is TdApi.MessageExpiredPhoto -> Unit
            is TdApi.MessageSticker -> {

            }
            is TdApi.MessageVideo -> {

            }
            is TdApi.MessageExpiredVideo -> Unit
            is TdApi.MessageVideoNote -> Unit
            is TdApi.MessageVoiceNote -> Unit
            is TdApi.MessageLocation -> Unit
            is TdApi.MessageVenue -> Unit
            is TdApi.MessageContact -> Unit
            is TdApi.MessageGame -> Unit
            is TdApi.MessagePoll -> Unit
            is TdApi.MessageInvoice -> Unit
            is TdApi.MessageCall -> Unit
            is TdApi.MessageBasicGroupChatCreate -> Unit
            is TdApi.MessageSupergroupChatCreate -> Unit
            is TdApi.MessageChatChangeTitle -> Unit
            is TdApi.MessageChatChangePhoto -> Unit
            is TdApi.MessageChatDeletePhoto -> Unit
            is TdApi.MessageChatAddMembers -> Unit
            is TdApi.MessageChatJoinByLink -> Unit
            is TdApi.MessageChatDeleteMember -> Unit
            is TdApi.MessageChatUpgradeTo -> Unit
            is TdApi.MessageChatUpgradeFrom -> Unit
            is TdApi.MessagePinMessage -> Unit
            is TdApi.MessageScreenshotTaken -> Unit
            is TdApi.MessageChatSetTtl -> Unit
            is TdApi.MessageCustomServiceAction -> Unit
            is TdApi.MessageGameScore -> Unit
            is TdApi.MessagePaymentSuccessful -> Unit
            is TdApi.MessagePaymentSuccessfulBot -> Unit
            is TdApi.MessageContactRegistered -> Unit
            is TdApi.MessageWebsiteConnected -> Unit
            is TdApi.MessagePassportDataSent -> Unit
            is TdApi.MessagePassportDataReceived -> Unit
            is TdApi.MessageUnsupported -> Unit
        }
    }

    companion object {
        val logger = createLogger<VkMessageAdapter>()
    }
}