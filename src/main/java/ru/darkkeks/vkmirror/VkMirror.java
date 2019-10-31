package ru.darkkeks.vkmirror;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.darkkeks.vkmirror.autoreg.BotDataManager;
import ru.darkkeks.vkmirror.autoreg.VkMirrorBot;
import ru.darkkeks.vkmirror.tdlib.TdApi;
import ru.darkkeks.vkmirror.tdlib.TelegramClient;
import ru.darkkeks.vkmirror.tdlib.TelegramCredentials;
import ru.darkkeks.vkmirror.vk.ChatType;
import ru.darkkeks.vkmirror.vk.UserLongPoll;
import ru.darkkeks.vkmirror.vk.VkController;
import ru.darkkeks.vkmirror.vk.object.Message;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

// TODO Think about possibility of someone else using this
//  Having a lot of accounts can be not manageable for a lot of people
//  Using one bot could be good use case if you dont mind bad look of chats
//  So a good idea would be to support both modes, but I don't feel like doing it right now, so I will just leave this
//  wall of text here, and maybe some day later I will look at it, return to this commit (#76cef1dc) and reuse a lot
//  of message sync logic.


// TODO Здесь я собираюсь попытаться описать алгоритм более абстрактно чтобы изменения старого кода не вызывали
//  |                         столько вопросов ://
//  |
//  |Два случая:
//  |  Сообщение пришло из вк
//  |    Получим объект соответствующий этому чатику из базы -- getChat()
//  |    Получим нужный клиент -- бота либо самого себя
//  |    Если это приватный чат, то:
//  |       Надо получить нужный id чата, учитывая что чат уже создан, мы уже знаем id -- это id получателя.
//  |       Отправляем сообщение
//  |    Если это групповой чат, то:
//  |       Если бота нету в чатике, то надо его добавить
//  |       Отправляем сообщение
//  |
//  |  Сообщение пришло из телеграма
//  |    ?
//  |
//  |getChat(vkPeerId):
//  |  Если объект с таким vkPeerId уже есть, достанем его из базы
//  |  Иначе
//  |    Если это приватный чат (vkPeerId -- id человека в вк):
//  |      Сделать бота -- getBotForVkUser(vkPeerId)
//  |      Поищем от моего имени бота и отправим /start, удалив сразу после этого (Боту надо либо не триггерится на
//  |             /start, либо как то помечать что бот еще не привязан и игнорить все сообщения)
//  |      Вернем объект, сохранив его в базу
//  |    Если это групповой чат (vkPeerId -- id чата привязанный ко мне):
//  |      Надо создать чатик -- createTelegramChat(vkPeerId, title) (от моего имени)
//  |      Если сообщение отправил не я, то надо добавить соответствующего бота в чатик:
//  |        Получим бота -- getBotForVkUser(message.from)
//  |      Вернем объект, сохранив его в базу
//  |
//  |getBotForVkUser()
//  |  Он будет либо уже привязан
//  |  Либо надо будет привязать нового бота к этому человеку
public class VkMirror {

    private static final Logger logger = LoggerFactory.getLogger(VkMirror.class);

    private BotDataManager botDataManager;

    private VkController vkController;
    private TelegramClient telegram;
    private VkMirrorDao vkMirrorDao;
    private ScheduledExecutorService executor;

    private Map<Integer, TelegramClient> clientsByVkId;

    private Map<Integer, Chat> chatsByPeerId;
    private Map<Integer, Chat> chatsByTelegramGroup;

    @Inject
    public VkMirror(BotDataManager botDataManager, VkController vkController, TelegramClient telegram, VkMirrorDao vkMirrorDao,
                    ScheduledExecutorService executor) {
        this.botDataManager = botDataManager;
        this.vkController = vkController;
        this.telegram = telegram;
        this.vkMirrorDao = vkMirrorDao;
        this.executor = executor;

        clientsByVkId = new HashMap<>();
        chatsByPeerId = new HashMap<>();
        chatsByTelegramGroup = new HashMap<>();
    }

    public void start() {
        telegram.onMessage(msg -> {
            if (msg.message.senderUserId == telegram.getMyId()) {
                this.executor.execute(() -> {
                    synchronized (this) {
                        onTelegramMessage(msg.message);
                    }
                });
            }
        });

        telegram.start();

        UserLongPoll longPoll = new UserLongPoll(vkController.getClient(), vkController.getActor(), this::onVkMessage);
        executor.submit(() -> vkController.run(longPoll));
    }

    /**
     * New Telegram message -> VK
     */
    private void onTelegramMessage(TdApi.Message message) {
        long chatId = message.chatId;

        if (message.senderUserId != telegram.getMyId()) return;

        TdApi.Chat chat = telegram.getChat(chatId);
        if (chat != null && chat.type instanceof TdApi.ChatTypeSupergroup) {
            int supergroupId = ((TdApi.ChatTypeSupergroup) chat.type).supergroupId;

            Chat mirrorChat = chatsByTelegramGroup.computeIfAbsent(supergroupId,
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
    private void onVkMessage(Message message) {
        logger.info("New message from {} -> id={} \"{}\"", message.getFrom(), message.getMessageId(), message.getText());

        getTelegramChat(message.getPeerId()).thenAccept((Chat chat) -> {
            if (chat == null) {
                logger.warn("Was unable to mirror message from vk to telegram chat, getTelegramChat returned null: {}",
                        message);
                return;
            }

            // TODO Chat direction;
            if (!vkMirrorDao.isSyncedVk(chat, message.getMessageId())) {
                CompletableFuture<TelegramClient> clientFuture;

                // Message from myself
                if ((message.getFlags() & Message.Flags.OUTBOX) > 0) {
                    clientFuture = CompletableFuture.completedFuture(telegram);
                } else {
                    // Message is from one of the chat participants
                    clientFuture = getBotForVkUser(message.getFrom())
                            .thenCompose(this::getBotClient);
                }

                clientFuture.thenAccept(client -> {
                    if (chat.getType() == ChatType.PRIVATE) {
                        synchronized (this) {
                            int recipientId = client == telegram ? chat.getTelegramId() : telegram.getMyId();
                            logger.info("Sending message {}", message.getText());
                            TdApi.Message tgMessage = client.sendMessage(recipientId, message.getText()).join();
                            logger.info("Message sent {}", tgMessage);
                            vkMirrorDao.saveMessage(chat, message.getMessageId(), tgMessage.id);
                            logger.info("Synced message {}!", tgMessage);
                        }
                    } else if (chat.getType() == ChatType.GROUP) {
                        client.groupById(chat.getTelegramId()).thenAccept(group -> {
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
                    }
                });
            }
        });
    }

    /**
     * Chat has to be from my point of view, otherwise every private chat would have the same id
     * But to send message from bot we need chat from bots' perspective :(
     * <p>
     * Lets get all chats from my point of view and apply some if-magic to it afterwards ?
     */
    private CompletableFuture<Chat> getTelegramChat(int vkPeerId) {
        Chat chat = chatsByPeerId.computeIfAbsent(vkPeerId, vkMirrorDao::getChatByVkPeer);

        if (chat != null) {
            return CompletableFuture.completedFuture(chat);

            // TODO Fix this check for groups and private chats
//            return telegram.groupById(chat.getTelegramId()).thenApply(group -> {
//                if (group == null) {
//                    logger.error("Saved group does not exist (Probably user left)");
//                }
//
//                return chat;
//            });
        } else if (vkController.isMultichat(vkPeerId)) {
            return CompletableFuture.supplyAsync(() -> {
                TdApi.Chat tgChat = createTelegramGroup(vkPeerId, vkController.getChannelTitle(vkPeerId)).join();
                TdApi.ChatTypeSupergroup supergroup = (TdApi.ChatTypeSupergroup) tgChat.type;

                Chat mirrorChat = Chat.groupChat(vkPeerId, supergroup.supergroupId);

                chatsByPeerId.put(vkPeerId, mirrorChat);
                chatsByTelegramGroup.put(supergroup.supergroupId, mirrorChat);

                vkMirrorDao.save(mirrorChat);
                return mirrorChat;
            });
        } else if (vkController.isPrivateChat(vkPeerId)) {
            return createPrivateChat(vkPeerId).thenApply(botClient -> {
                if (botClient == null) {
                    return null;
                }

                Chat mirrorChat = Chat.privateChat(vkPeerId, botClient.getMyId());

                chatsByPeerId.put(vkPeerId, mirrorChat);

                vkMirrorDao.save(mirrorChat);
                return mirrorChat;
            });
        }

        throw new UnsupportedOperationException("Group chats are not supported yet");
    }

    private CompletableFuture<TdApi.Chat> createTelegramGroup(int vkPeerId, String title) {
        String chatUrl = vkController.getChatUrl(vkPeerId);
        return telegram.createChannel(title, chatUrl).thenApply(chat -> {
            TdApi.ChatType type = chat.type;
            if (type instanceof TdApi.ChatTypeSupergroup) {
                // TODO Should join every person that is already bound to a bot;
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

    private CompletableFuture<TelegramClient> createPrivateChat(int vkPeerId) {
        return getBotForVkUser(vkPeerId).thenCompose(bot -> {
            if (bot == null) {
                return CompletableFuture.completedFuture(null);
            }

            return telegram.searchPublicUsername(bot.getUsername()).thenCompose(chat -> {
                // We assume bot will delete this message for both of us
                telegram.sendMessage(chat.id, "/start");
                return getBotClient(bot);
            });
        });
    }

    private CompletableFuture<VkMirrorBot> getBotForVkUser(int vkId) {
        return botDataManager.getBotForVk(vkId);
    }

    private CompletableFuture<TelegramClient> getBotClient(VkMirrorBot bot) {
        if (clientsByVkId.containsKey(bot.getVkId())) {
            return CompletableFuture.completedFuture(clientsByVkId.get(bot.getVkId()));
        } else {
            return CompletableFuture.supplyAsync(() -> {
                TelegramClient client = new TelegramClient(TelegramCredentials.bot(
                        Config.API_ID, Config.API_HASH, bot.getToken()));

                // TODO Test if bot can actually revoke this for me
                client.onMessage(newMessage -> {
                    TdApi.Message message = newMessage.message;
                    if (message.content instanceof TdApi.MessageText) {
                        TdApi.FormattedText text = ((TdApi.MessageText) message.content).text;
                        if (text.text.equals("/start")) {
                            client.deleteMessage(message.chatId, true, message.id).exceptionally(e -> {
                                logger.error("Failed to delete /start message", e);
                                return null;
                            });
                        }
                    }
                });

                client.start();

                clientsByVkId.put(bot.getVkId(), client);
                return client;
            });
        }
    }
}
