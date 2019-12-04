package ru.darkkeks.vkmirror.vk.`object`

import com.google.gson.annotations.SerializedName
import com.vk.api.sdk.objects.messages.Conversation

class MyConversation : Conversation() {
    @SerializedName("chat_settings")
    val chatSettings: ChatSettings? = null
}