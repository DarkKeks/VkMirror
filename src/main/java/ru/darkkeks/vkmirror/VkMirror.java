package ru.darkkeks.vkmirror;

import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.groups.GroupFull;
import com.vk.api.sdk.objects.messages.responses.GetChatPreviewResponse;
import com.vk.api.sdk.objects.users.UserXtrCounters;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.darkkeks.vkmirror.autoreg.BotDao;
import ru.darkkeks.vkmirror.autoreg.BotDataManager;
import ru.darkkeks.vkmirror.tdlib.TdApi;
import ru.darkkeks.vkmirror.vk.VkController;
import ru.darkkeks.vkmirror.vk.object.Message;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

// TODO Think about possibility of someone else using this
//  Having a lot of accounts can be not manageable for a lot of people
//  Using one bot could be good use case if you dont mind bad look of chats
//  So a good idea would be to support both modes, but I don't feel like doing it right now, so I will just like this
//  wall of text here, and maybe some day later I will look at it, return to this commit (#76cef1dc) and reuse a lot
//  of message sync logic.
public class VkMirror {

    private static final Logger logger = LoggerFactory.getLogger(VkMirror.class);
    private final BotDataManager botDataManager;

    private VkController vkController;
    private VkMirrorTelegram telegram;
    private VkMirrorDao vkMirrorDao;
    private ScheduledThreadPoolExecutor executor;

    private Map<Integer, VkMirrorTelegram> clientsByVkId;

    private Map<Integer, VkMirrorChat> chatsByPeerId;
    private Map<Integer, VkMirrorChat> chatsByTelegramGroup;

    public static void main(String[] args) {
        new VkMirror();
    }

    public VkMirror() {
        executor = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
        vkController = new VkController(Config.USER_ID, Config.USER_TOKEN);

        logger.info("Creating telegram");
        telegram = new VkMirrorTelegram(Config.API_ID, Config.API_HASH, false, Config.PHONE_NUMBER);

//        logger.info("Creating bot");
//        bot = new VkMirrorTelegram(Config.API_ID, Config.API_HASH, true, Config.BOT_TOKEN);

        telegram.onMessage(msg -> {
            if (msg.message.senderUserId == telegram.getMyId()) {
                executor.execute(() -> {
                    synchronized (this) {
                        newReply(msg.message);
                    }
                });
            }
        });

        HikariDataSource dataSource = Config.createDataSource();
        vkMirrorDao = new VkMirrorDao(dataSource);
        botDataManager = new BotDataManager(new BotDao(dataSource));

        VkMirrorLongPoll longPoll = new VkMirrorLongPoll(this, vkController.getClient(), vkController.getActor());

        clientsByVkId = new HashMap<>();
        chatsByPeerId = new HashMap<>();
        chatsByTelegramGroup = new HashMap<>();

        executor.submit(() -> vkController.run(longPoll));
    }

    /**
     * New Telegram message -> VK
     */
    private void newReply(TdApi.Message message) {
        long chatId = message.chatId;

        if (message.senderUserId != telegram.getMyId()) return;

        TdApi.Chat chat = telegram.getChat(chatId);
        if (chat != null && chat.type instanceof TdApi.ChatTypeSupergroup) {
            int supergroupId = ((TdApi.ChatTypeSupergroup) chat.type).supergroupId;

            VkMirrorChat mirrorChat = chatsByTelegramGroup.computeIfAbsent(supergroupId,
                    vkMirrorDao::getChatByTelegramGroup);

            if (mirrorChat != null) {
                logger.info("Received message from myself id={} \"{}\"", message.id, message.content);
                if (!vkMirrorDao.isSyncedTelegram(mirrorChat, message.id)) {
                    logger.info("Message {} is not synced", message.id);
                    List<Integer> ids = vkController.sendMessage(mirrorChat.getVkPeerId(), message);
                    logger.info("Produced vk messages: {}", ids.toString());
                    ids.forEach(id -> {
                        vkMirrorDao.saveMessage(mirrorChat, id, message.id);
                    });
                }
            }
        }
    }

    /**
     * VK message -> Telegram (both from me and other chat participants)
     */
    public void sendMessage(VkMirrorChat chat, Message message) {
        if (!vkMirrorDao.isSyncedVk(chat, message.getMessageId())) {
            CompletableFuture<VkMirrorTelegram> clientFuture;

            // Message from myself
            if ((message.getFlags() & Message.Flags.OUTBOX) > 0) {
                clientFuture = CompletableFuture.completedFuture(telegram);
            } else {
                // Message is from one of the chat participants
                clientFuture = getBotForVkUser(message.getFrom());
            }

            clientFuture.thenAccept(client -> {
                client.groupById(chat.getTelegramChannelId()).thenAccept(group -> {
                    logger.info("Sending to chat {}", group.id);
                    executor.submit(() -> {
                        synchronized (this) {
                            TdApi.Message tgMessage = client.sendMessage(group.id, message.getText()).join();
                            logger.info("Message sent {}", tgMessage);
                            vkMirrorDao.saveMessage(chat, message.getMessageId(), tgMessage.id);
                            logger.info("Synced message {}!", tgMessage);
                        }
                    });
                });
            });
        }
    }

    public CompletableFuture<VkMirrorChat> getTelegramChat(int vkPeerId) {
        VkMirrorChat chat = chatsByPeerId.computeIfAbsent(vkPeerId, vkMirrorDao::getChatByVkPeer);

        if (chat != null) {
            return telegram.groupById(chat.getTelegramChannelId()).thenApply(group -> {
                if (group == null) {
                    logger.error("Saved group does not exist (Probably user left)");
                }
                return chat;
            });
        } else {
            return CompletableFuture.supplyAsync(() -> {
                TdApi.Chat tgChat = createTelegramChat(vkPeerId, getChannelTitle(vkPeerId)).join();
                TdApi.ChatTypeSupergroup supergroup = (TdApi.ChatTypeSupergroup) tgChat.type;

                VkMirrorChat mirrorChat = new VkMirrorChat(vkPeerId, supergroup.supergroupId);

                chatsByPeerId.put(vkPeerId, mirrorChat);
                chatsByTelegramGroup.put(supergroup.supergroupId, mirrorChat);

                vkMirrorDao.save(mirrorChat);
                return mirrorChat;
            });
        }
    }

    private CompletableFuture<TdApi.Chat> createTelegramChat(int vkPeerId, String title) {
        return telegram.createChannel(title, vkController.getChatUrl(vkPeerId)).thenApply(chat -> {
            TdApi.ChatType type = chat.type;
            if (type instanceof TdApi.ChatTypeSupergroup) {
                // TODO Shoould join every person that is already bound to a bot;
                //  Other people should be added only on activity in chat
//                telegram.chatAddUser(chat.id, bot.getMyId()).join();

                Path photo = vkController.downloadConversationImage(vkPeerId);
                if (photo != null) {
                    telegram.setGroupPhoto(chat.id, photo).join();
                }

            } else {
                throw new IllegalStateException("New chat is not channel");
            }
            return chat;
        });
    }

    private CompletableFuture<VkMirrorTelegram> getBotForVkUser(int vkId) {
        if (clientsByVkId.containsKey(vkId)) {
            return CompletableFuture.completedFuture(clientsByVkId.get(vkId));
        } else {
            return botDataManager.getBotForVk(vkId).thenApply(botInfo -> {
                if(botInfo == null) return null;

                VkMirrorTelegram client = new VkMirrorTelegram(Config.API_ID, Config.API_HASH, true, botInfo.getToken());
                clientsByVkId.put(vkId, client);
                return client;
            });
        }
    }

    private String getChannelTitle(int peerId) {
        try {
            if (vkController.isMultichat(peerId)) {
                GetChatPreviewResponse chat =
                        vkController.getClient().messages().getChatPreview(vkController.getActor())
                        .peerId(peerId)
                        .execute();
                return chat.getPreview().getTitle();
            } else if (vkController.isGroup(peerId)) {
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
