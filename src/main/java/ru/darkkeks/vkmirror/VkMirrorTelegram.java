package ru.darkkeks.vkmirror;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.darkkeks.vkmirror.tdlib.AuthHandler;
import ru.darkkeks.vkmirror.tdlib.Client;
import ru.darkkeks.vkmirror.tdlib.OrderedChat;
import ru.darkkeks.vkmirror.tdlib.TdApi;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class VkMirrorTelegram {

    private static final Logger logger = LoggerFactory.getLogger(VkMirrorTelegram.class);

    private Client client;
    private AuthHandler authHandler;

    private Map<Integer, UpdateHandlerHolder<?>> updateHandlers;

    private Map<String, Integer> options;

    private Map<Long, TdApi.Chat> chats;
    private NavigableSet<OrderedChat> orderedChats;
    private Map<Integer, TdApi.Supergroup> supergroups;

    public VkMirrorTelegram(int apiId, String apiHash, boolean isBot) {
        updateHandlers = new HashMap<>();
        supergroups = new ConcurrentHashMap<>();
        orderedChats = new TreeSet<>();
        chats = new HashMap<>();
        options = new HashMap<>();

        Client.execute(new TdApi.SetLogVerbosityLevel(0));
        Client.execute(new TdApi.SetLogStream(new TdApi.LogStreamFile("tdlib.log", 1 << 27)));

        if(isBot) {
            authHandler = AuthHandler.bot(this, apiId, apiHash, Config.BOT_TOKEN);
        } else {
            authHandler = AuthHandler.phone(this, apiId, apiHash, Config.PHONE_NUMBER);
        }
        addHandler(TdApi.UpdateAuthorizationState.CONSTRUCTOR, authHandler, TdApi.UpdateAuthorizationState.class);
        addHandler(TdApi.UpdateOption.CONSTRUCTOR, update -> {
            if(update.value instanceof TdApi.OptionValueInteger) {
                options.put(update.name, ((TdApi.OptionValueInteger) update.value).value);
            }
        }, TdApi.UpdateOption.class);
        addHandler(TdApi.UpdateNewChat.CONSTRUCTOR, update -> {
            TdApi.Chat chat = update.chat;
            chats.put(chat.id, chat);
            updateChat(chat, chat.order);
        }, TdApi.UpdateNewChat.class);
        addHandler(TdApi.UpdateSupergroup.CONSTRUCTOR, update -> {
            supergroups.put(update.supergroup.id, update.supergroup);
        }, TdApi.UpdateSupergroup.class);
        addHandler(TdApi.UpdateChatDraftMessage.CONSTRUCTOR, update -> {
            updateChat(chats.get(update.chatId), update.order);
        }, TdApi.UpdateChatDraftMessage.class);
        addHandler(TdApi.UpdateChatIsPinned.CONSTRUCTOR, update -> {
            updateChat(chats.get(update.chatId), update.order);
        }, TdApi.UpdateChatIsPinned.class);
        addHandler(TdApi.UpdateChatOrder.CONSTRUCTOR, update -> {
            updateChat(chats.get(update.chatId), update.order);
        }, TdApi.UpdateChatOrder.class);
        addHandler(TdApi.UpdateChatLastMessage.CONSTRUCTOR, update -> {
            updateChat(chats.get(update.chatId), update.order);
        }, TdApi.UpdateChatLastMessage.class);

        client = Client.create(this::handlerUpdate, this::handleException, this::handleException);
        authHandler.awaitAuthorization();
    }

    private <T extends TdApi.Update> void addHandler(int constructor, Consumer<T> handler, Class<T> clazz) {
        updateHandlers.put(constructor, new UpdateHandlerHolder<>(clazz, handler));
    }

    private void updateChat(TdApi.Chat chat, long newOrder) {
        orderedChats.remove(new OrderedChat(chat.id, chat.order));
        chat.order = newOrder;
        orderedChats.add(new OrderedChat(chat.id, chat.order));
    }

    private void handlerUpdate(TdApi.Object object) {
        logger.info(object.getClass().getName());

        UpdateHandlerHolder<?> handler = updateHandlers.get(object.getConstructor());
        if(handler != null) {
            handler.accept((TdApi.Update) object);
        }
    }

    private void handleException(Throwable exception) {
        logger.error("Exception happened", exception);
    }

    public int getMyId() {
        return options.get("my_id");
    }

    public CompletableFuture<Void> chatAddUser(long id, int userId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        client.send(new TdApi.GetUser(userId), user -> {
            logger.info("{} {}", id, userId);
            client.send(new TdApi.AddChatMember(id, userId, 100), result -> {
                logger.info("Result: {}", result);
                future.complete(null);
            });
        });

        return future;
    }

    public CompletableFuture<TdApi.Chat> createChannel(String title, String description) {
        CompletableFuture<TdApi.Chat> future = new CompletableFuture<>();
        client.send(new TdApi.CreateNewSupergroupChat(title, false, description), result -> {
            if(result instanceof TdApi.Error) {
                logger.error("Error creating channel {}: {} ", title, result);
                future.completeExceptionally(new IllegalStateException());
            } else {
                logger.info(result.toString());
                future.complete((TdApi.Chat) result);
            }
        });
        return future;
    }

    public void sendMessage(long chatId, String text) {
        client.send(new TdApi.SendMessage(chatId, 0, true, true, null,
                new TdApi.InputMessageText(new TdApi.FormattedText(text, null), false, true)),
                System.out::println);
    }

    public CompletableFuture<TdApi.Chat> groupById(int channelId) {
        CompletableFuture<TdApi.Chat> future = new CompletableFuture<>();
        client.send(new TdApi.CreateSupergroupChat(channelId, false), result -> {
            if(result instanceof TdApi.Error) {
                future.completeExceptionally(new IllegalStateException(result.toString()));
            } else {
                future.complete((TdApi.Chat) result);
            }
        });

        return future;
    }

    public CompletableFuture<Void> preloadChat(long chatId) {
        if(chats.containsKey(chatId)) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        long offsetOrder = Long.MAX_VALUE;
        long offsetChatId = 0;
        if(!orderedChats.isEmpty()) {
            offsetOrder = orderedChats.last().getOrder();
            offsetChatId = orderedChats.last().getChatId();
        }
        client.send(new TdApi.GetChats(offsetOrder, offsetChatId, 20), result -> {
            if(result instanceof TdApi.Error) {
                logger.error("Error while loading chats {}", result);
            } else {
                long[] chatIds = ((TdApi.Chats) result).chatIds;
                for(long id : chatIds) {
                    if(id == chatId) {
                        future.complete(null);
                        return;
                    }
                }
                if(chatIds.length > 0) {
                    preloadChat(chatId).thenAccept(future::complete);
                } else {
                    future.completeExceptionally(new IllegalArgumentException("Chat not found"));
                }
            }
        });

        return future;
    }

    private static class UpdateHandlerHolder<T extends TdApi.Update> {
        private Class<T> clazz;
        private Consumer<T> handler;

        public UpdateHandlerHolder(Class<T> clazz, Consumer<T> handler) {
            this.clazz = clazz;
            this.handler = handler;
        }

        public void accept(TdApi.Update update) {
            handler.accept(clazz.cast(update));
        }
    }

    public Client getClient() {
        return client;
    }
}
