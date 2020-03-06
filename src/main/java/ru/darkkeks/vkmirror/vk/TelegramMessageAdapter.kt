package ru.darkkeks.vkmirror.vk

import com.vk.api.sdk.objects.messages.MessageAttachmentType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import ru.darkkeks.vkmirror.tdlib.TelegramClient
import ru.darkkeks.vkmirror.tdlib.internal.TdApi
import ru.darkkeks.vkmirror.util.buildList
import ru.darkkeks.vkmirror.vk.`object`.Message

class TelegramMessageAdapter(val vk: VkController, kodein: Kodein) {

    /**
     * | [4,762425,49,456171173,1579620947,"жопа",{"title":" ... "},{}]
     * | [4,762426,51,456171173,1579620950,"🤔",{"emoji":"1","title":" ... "},{}]
     * | [4,762427,51,456171173,1579620961,"",{"title":" ... "},{"attach1_type":"photo","attach1":"159179937_457240383"}]
     * | [4,762428,51,456171173,1579620981,"жопа",{"title":" ... "},{"attach1_type":"photo","attach1":"159179937_457240384"}]
     * | [4,762429,51,456171173,1579620991,"",{"title":" ... "},{"attach1_type":"doc","attach1":"159179937_532412577"}]
     * | [4,762430,51,456171173,1579621001,"жопа",{"title":" ... "},{"attach1_type":"doc","attach1":"159179937_532412577"}]
     * | [4,762433,2097203,456171173,1579624589,"фывафыва",{"title":" ... "},{"fwd":"0_0"}]
     * | [4,762434,2097201,456171173,1579624614,"asdfadsf",{"title":" ... "},{"fwd":"0_0"}]
     * | [4,762435,49,456171173,1579624624,"жопа",{"title":" ... "},{"fwd":"0_0"}]
     * | [4,762438,51,456171173,1579625751,"",{"title":" ... "},{"attach1_product_id":"121","attach1_type":"sticker","attach1":"3670"}]
     * | [4,762440,51,456171173,1579630989,"",{"title":" ... "},{"attach1_type":"photo","attach1":"159179937_456240241","attach2_type":"photo","attach2":"159179937_456239506","attach3_type":"photo","attach3":"159179937_456239109","attach4_type":"photo","attach4":"159179937_456239039","attach5_type":"photo","attach5":"159179937_456239035","attach6_type":"photo","attach6":"159179937_456239036","attach7_type":"photo","attach7":"159179937_456239037","attach8_type":"photo","attach8":"159179937_456239038","attach9_type":"photo","attach9":"159179937_456239034","attach10_type":"photo","attach10":"159179937_436257210"}]
     */

    val telegram: TelegramClient by kodein.instance()

    suspend fun adapt(message: Message) = buildList<TdApi.InputMessageContent> {
        val photos = message.attachmentIds.filter { it.type == Message.AttachmentType.PHOTO }
        when {
            photos.isNotEmpty() -> {
                coroutineScope<Unit> {
                    val messageInfo = vk.getMessageById(message.messageId)

                    if (messageInfo == null) {
                        // fallback to text
                        if (message.text.isNotEmpty()) {
                            add(TdApi.InputMessageText())
                        }
                    } else {
                        messageInfo.attachments.filter {
                            it.type == MessageAttachmentType.PHOTO
                        }.map { attachment ->
                            async {
                                val photo = attachment.photo.images.maxBy { it.height * it.width } ?: return@async null
                                val path = vk.downloadFile(photo.url) ?: return@async null
                                val file = TdApi.InputFileLocal(path.toAbsolutePath().toString())

                                TdApi.InputMessagePhoto(file, null, intArrayOf(), photo.width, photo.height, TdApi.FormattedText(attachment.photo.text, arrayOf()), 0)
                            }
                        }.mapNotNull {
                            it.await()
                        }.map {
                            add(it)
                        }
                    }
                }
            }
            message.text.isNotBlank() -> {
                add(textMessage(message.text))
            }
        }
    }

    private fun textMessage(text: String): TdApi.InputMessageText {
        return TdApi.InputMessageText(TdApi.FormattedText(text, arrayOf()), false, false)
    }


    private fun emptyLink(url: String) = """
        <a href="$url">${"\u00A0"}</a>
    """.trimIndent()

}