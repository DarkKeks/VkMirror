package ru.darkkeks.vkmirror.vk.object;

import com.google.gson.annotations.SerializedName;
import com.vk.api.sdk.objects.messages.Conversation;

public class MyConversation extends Conversation {

    @SerializedName("chat_settings")
    private ChatSettings chatSettings;

    public ChatSettings getChatSettings() {
        return chatSettings;
    }
}
