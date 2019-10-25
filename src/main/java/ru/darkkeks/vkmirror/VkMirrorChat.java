package ru.darkkeks.vkmirror;

import ru.darkkeks.vkmirror.vk.ChatType;

public class VkMirrorChat {

    private int id;
    private int vkPeerId;
    private int telegramId;

    private ChatType type;

    public VkMirrorChat(int id, int vkPeerId, int telegramId, ChatType type) {
        this.id = id;
        this.vkPeerId = vkPeerId;
        this.telegramId = telegramId;
        this.type = type;
    }

    public static VkMirrorChat privateChat(int vkPeerId, int botId) {
        return new VkMirrorChat(-1, vkPeerId, botId, ChatType.PRIVATE);
    }

    public static VkMirrorChat groupChat(int vkPeerId, int telegramChatId) {
        return new VkMirrorChat(-1, vkPeerId, telegramChatId, ChatType.GROUP);
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public int getVkPeerId() {
        return vkPeerId;
    }

    public int getTelegramId() {
        return telegramId;
    }

    public ChatType getType() {
        return type;
    }
}
