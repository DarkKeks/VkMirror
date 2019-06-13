package ru.darkkeks.vkmirror;

public class VkMirrorChat {

    private int vkPeerId;
    private int telegramChannelId;

    public VkMirrorChat(int vkPeerId, int telegramChannelId) {
        this.vkPeerId = vkPeerId;
        this.telegramChannelId = telegramChannelId;
    }

    public int getVkPeerId() {
        return vkPeerId;
    }

    public int getTelegramChannelId() {
        return telegramChannelId;
    }
}
