package ru.darkkeks.vkmirror;

import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.groups.GroupFull;
import com.vk.api.sdk.objects.messages.responses.GetChatPreviewResponse;
import com.vk.api.sdk.objects.users.UserXtrCounters;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import ru.darkkeks.vkmirror.tdlib.TdApi;
import ru.darkkeks.vkmirror.vk.VkController;
import ru.darkkeks.vkmirror.vk.object.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class VkMirror {

    private static final Logger logger = LoggerFactory.getLogger(VkMirror.class);

    private VkController vkController;
    private VkMirrorTelegram telegram;
    private VkMirrorDao vkMirrorDao;
    private VkMirrorTelegram bot;
    private ScheduledThreadPoolExecutor executor;

    private Map<Integer, VkMirrorChat> chats;

    public VkMirror() {
        executor = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
        vkController = new VkController(Config.USER_ID, Config.USER_TOKEN);
        logger.info("Creating telegram");
        telegram = new VkMirrorTelegram(Config.API_ID, Config.API_HASH, false);
        logger.info("Creating bot");
        bot = new VkMirrorTelegram(Config.API_ID, Config.API_HASH, true);
        vkMirrorDao = new VkMirrorDao(Config.createDataSource());

        VkMirrorLongPoll longPoll = new VkMirrorLongPoll(this, vkController.getClient(), vkController.getActor());

        chats = new HashMap<>();

        executor.submit(() -> vkController.run(longPoll));
    }

    public void sendMessage(int groupId, Message message) {
        bot.groupById(groupId).thenAccept(g -> {
            logger.info("Sending to chat {}", g.id);
            bot.sendMessage(g.id, message.getText());
        });
    }

    public CompletableFuture<TdApi.Chat> getTelegramChat(int vkPeerId) {
        VkMirrorChat chat = chats.computeIfAbsent(vkPeerId, vkMirrorDao::getChat);
        if(chat != null) {
            return telegram.groupById(chat.getTelegramChannelId());
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<TdApi.Chat> createTelegramChat(int vkPeerId, String title) {
        return telegram.createChannel(title, vkController.getChatUrl(vkPeerId)).thenApply(chat -> {
            System.out.println("Created chat");
            TdApi.ChatType type = chat.type;
            if(type instanceof TdApi.ChatTypeSupergroup) {
                TdApi.ChatTypeSupergroup supergroup = (TdApi.ChatTypeSupergroup) type;
                VkMirrorChat mirrorChat = new VkMirrorChat(vkPeerId, supergroup.supergroupId);
                chats.put(vkPeerId, mirrorChat);
                vkMirrorDao.save(mirrorChat);

                telegram.chatAddUser(chat.id, bot.getMyId());

                // TODO Set photo
            } else {
                throw new IllegalStateException("New chat is not channel");
            }
            return chat;
        });
    }

    public String getChannelTitle(int peerId) {
        try {
            if(vkController.isMultichat(peerId)) {
                GetChatPreviewResponse chat = vkController.getClient().messages().getChatPreview(vkController.getActor())
                        .peerId(peerId)
                        .execute();
                return chat.getPreview().getTitle();
            } else if(vkController.isGroup(peerId)) {
                GroupFull group = vkController.getClient().groups().getById(vkController.getActor())
                        .groupId(Integer.toString(-peerId))
                        .execute().get(0);
                return group.getName();
            } else {
                UserXtrCounters user = vkController.getClient().users().get(vkController.getActor())
                        .userIds(Integer.toString(peerId))
                        .execute().get(0);

                return String.format("%s %s", user.getFirstName(), user.getLastName());
            }
        } catch (ClientException | ApiException e) {
            logger.error("Cant get channel title {}: ", peerId, e);
            return null;
        }
    }
}
