package ru.darkkeks.vkmirror.vk

import com.vk.api.sdk.queries.messages.MessagesSendQuery
import ru.darkkeks.vkmirror.tdlib.TdApi

class VkMessageAdapter(val queryProvider: () -> MessagesSendQuery) {

    fun adapt(message: TdApi.Message): List<MessagesSendQuery> {
        val result = mutableListOf<MessagesSendQuery>()
        when (val content = message.content) {
            is TdApi.MessageText -> {
                result.add(queryProvider().message(content.text.text))
            }
        }
        return result
    }

}