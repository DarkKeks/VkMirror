package ru.darkkeks.vkmirror;

public class VkMirrorChat {

    private int id;
    private int vkPeerId;
    private int telegramChannelId;

    public VkMirrorChat(int vkPeerId, int telegramChannelId) {
        this(-1, vkPeerId, telegramChannelId);
    }

    public VkMirrorChat(int id, int vkPeerId, int telegramChannelId) {
        this.id = id;
        this.vkPeerId = vkPeerId;
        this.telegramChannelId = telegramChannelId;
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

    public int getTelegramChannelId() {
        return telegramChannelId;
    }
}
