package ru.darkkeks.vkmirror;


import ru.darkkeks.vkmirror.vk.ChatType;

/**
 * An object describing the relationship between vk peer id (person, multichat or group) and telegram chat (bot or
 * supergroup)
 */
public class Chat {

    private int id;
    private int vkPeerId;
    private int telegramId;

    private ChatType type;

    public Chat(int id, int vkPeerId, int telegramId, ChatType type) {
        this.id = id;
        this.vkPeerId = vkPeerId;
        this.telegramId = telegramId;
        this.type = type;
    }

    public static Chat privateChat(int vkPeerId, int botId) {
        return new Chat(-1, vkPeerId, botId, ChatType.PRIVATE);
    }

    public static Chat groupChat(int vkPeerId, int telegramChatId) {
        return new Chat(-1, vkPeerId, telegramChatId, ChatType.GROUP);
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
