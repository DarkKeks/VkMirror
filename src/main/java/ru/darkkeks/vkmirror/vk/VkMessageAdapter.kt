package ru.darkkeks.vkmirror.vk

import com.vk.api.sdk.queries.messages.MessagesSendQuery
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import ru.darkkeks.vkmirror.tdlib.TelegramClient
import ru.darkkeks.vkmirror.tdlib.internal.TdApi
import ru.darkkeks.vkmirror.util.buildList
import ru.darkkeks.vkmirror.util.createLogger

class VkMessageAdapter(val vk: VkController, kodein: Kodein) {

    val telegram: TelegramClient by kodein.instance()

    suspend fun adapt(peerId: Int,
                      message: TdApi.Message,
                      queryProvider: () -> MessagesSendQuery) = buildList<MessagesSendQuery> {

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
                val query = queryProvider()

                val send = processAttachment(text, content.animation.animation, query) {
                    when (content.animation.mimeType) {
                        "image/gif" -> vk.uploadPhotoAttachment(peerId, it)
                        "video/mp4" -> vk.uploadVideoAttachment(it)
                        else -> null
                    }
                }

                if (send) {
                    add(query)
                }
            }
            is TdApi.MessageAudio -> Unit
            is TdApi.MessageDocument -> {
                val query = queryProvider()
                val text = content.caption.text
                val document = content.document.document
                if (processAttachment(text, document, query) { vk.uploadDocumentAttachment(peerId, it) }) {
                    add(query)
                }
            }
            is TdApi.MessagePhoto -> {
                val text = content.caption.text
                val query = queryProvider()
                val file = content.photo.sizes.maxBy { it.height * it.width }
                        ?: throw IllegalStateException("Photo without sizes")
                if (processAttachment(text, file.photo, query) { vk.uploadPhotoAttachment(peerId, it) }) {
                    add(query)
                }
            }
            is TdApi.MessageExpiredPhoto -> Unit
            is TdApi.MessageSticker -> {
                val query = queryProvider()
                val file = content.sticker.thumbnail.photo
                if (processAttachment(null, file, query) { vk.uploadPhotoAttachment(peerId, it) }) {
                    add(query)
                }
            }
            is TdApi.MessageVideo -> {
                val query = queryProvider()
                val text = content.caption.text
                if (processAttachment(text, content.video.video, query) { vk.uploadVideoAttachment(it) }) {
                    add(query)
                }
            }
            is TdApi.MessageExpiredVideo -> Unit
            is TdApi.MessageVideoNote -> {
                val query = queryProvider()
                if (processAttachment(null, content.videoNote.video, query) { vk.uploadVideoAttachment(it) }) {
                    add(query)
                }
            }
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

    private suspend fun processAttachment(text: String?, file: TdApi.File, query: MessagesSendQuery, attach: suspend (String) -> String?): Boolean {
        query.dontParseLinks(true)

        val nonEmptyText = if (text == null || text.isBlank()) null else text


        val downloaded = telegram.downloadFile(file)
        logger.info("Downloaded file: {}", file)

        val attachment = if (downloaded.local.isDownloadingCompleted) {
            attach(downloaded.local.path)
        } else {
            logger.error("Failed to download file {}", downloaded)
            null
        }

        if (nonEmptyText != null) {
            query.message(nonEmptyText)
        }

        if (attachment != null) {
            query.attachment(attachment)
        }

        return nonEmptyText != null || attachment != null
    }

    companion object {
        val logger = createLogger<VkMessageAdapter>()
    }
}