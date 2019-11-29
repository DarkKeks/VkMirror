package ru.darkkeks.vkmirror.tdlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class TelegramClient {

    private static final Logger logger = LoggerFactory.getLogger(TelegramClient.class);
    private final AuthHandler authHandler;

    private Client client;

    private Map<String, Integer> options;

    private Map<Long, TdApi.Chat> chats;
    private Map<Integer, TdApi.Supergroup> supergroups;

    private NavigableSet<OrderedChat> orderedChats;

    private Map<Class<?>, List<UpdateHandlerHolder<?>>> updateHandlers;

    public TelegramClient(TelegramCredentials credentials) {
        updateHandlers = new HashMap<>();
        supergroups = new ConcurrentHashMap<>();
        orderedChats = new TreeSet<>();
        options = new HashMap<>();
        chats = new HashMap<>();

        Client.execute(new TdApi.SetLogStream(new TdApi.LogStreamFile("tdlib.log", 1 << 27)));
        Client.execute(new TdApi.SetLogVerbosityLevel(5));

        authHandler = new AuthHandler(this, credentials);
        addHandler(TdApi.UpdateAuthorizationState.class, authHandler);

        addHandler(TdApi.UpdateOption.class, update -> {
            if(update.value instanceof TdApi.OptionValueInteger) {
                options.put(update.name, ((TdApi.OptionValueInteger) update.value).value);
            }
        });

        addHandler(TdApi.UpdateNewChat.class, update -> {
            TdApi.Chat chat = update.chat;
            chats.put(chat.id, chat);
            updateChat(chat, chat.order);
        });

        addHandler(TdApi.UpdateSupergroup.class, update -> {
            supergroups.put(update.supergroup.id, update.supergroup);
        });

        addHandler(TdApi.UpdateChatDraftMessage.class, update -> {
            updateChat(chats.get(update.chatId), update.order);
        });

        addHandler(TdApi.UpdateChatIsPinned.class, update -> {
            updateChat(chats.get(update.chatId), update.order);
        });

        addHandler(TdApi.UpdateChatOrder.class, update -> {
            updateChat(chats.get(update.chatId), update.order);
        });

        addHandler(TdApi.UpdateChatLastMessage.class, update -> {
            updateChat(chats.get(update.chatId), update.order);
        });
    }

    public void start() {
        client = Client.create(this::handlerUpdate, this::handleException);
        authHandler.awaitAuthorization();
    }

    private <T extends TdApi.Update> void addHandler(Class<T> clazz, Consumer<T> handler) {
        updateHandlers.computeIfAbsent(clazz, c -> new ArrayList<>()).add(new UpdateHandlerHolder<>(clazz, handler));
    }

    private void updateChat(TdApi.Chat chat, long newOrder) {
        orderedChats.remove(new OrderedChat(chat.id, chat.order));
        chat.order = newOrder;
        orderedChats.add(new OrderedChat(chat.id, chat.order));
    }

    private void handlerUpdate(TdApi.Object object) {
        List<UpdateHandlerHolder<?>> handlers = updateHandlers.get(object.getClass());
        if(handlers != null) {
            handlers.forEach(h -> h.accept(((TdApi.Update) object)));
        }
    }

    private void handleException(Throwable exception) {
        logger.error("Exception happened", exception);
    }

    public void onMessage(Consumer<TdApi.UpdateNewMessage> handler) {
        addHandler(TdApi.UpdateNewMessage.class, handler);
    }

    public int getMyId() {
        return Optional.ofNullable(options.get("my_id")).orElse(-1);
    }

    public TdApi.Chat getChat(long chatId) {
        return chats.get(chatId);
    }

    public CompletableFuture<Void> setGroupPhoto(long chatId, Path file) {
        return client.send(new TdApi.SetChatPhoto(chatId, new TdApi.InputFileLocal(file.toAbsolutePath().toString())))
                .thenAccept(x -> {});
    }

    public CompletableFuture<TdApi.User> getUser(int userId) {
        return client.send(new TdApi.GetUser(userId)).thenApply(object -> (TdApi.User) object);
    }

    public CompletableFuture<Void> chatAddUser(long id, int userId) {
        return getUser(userId).thenCompose(user -> {
            return client.send(new TdApi.AddChatMember(id, userId, 100)).thenAccept(x -> {});
        });
    }

    public CompletableFuture<TdApi.Chat> createSupergroup(String title, String description) {
        return client.send(new TdApi.CreateNewSupergroupChat(title, false, description)).thenApply(result -> {
            if(result instanceof TdApi.Error) {
                logger.error("Error creating channel {}: {} ", title, result);
                throw new IllegalStateException();
            }
            return (TdApi.Chat) result;
        });
    }

    public CompletableFuture<TdApi.Chat> createPrivateChat(int userId) {
        return client.send(new TdApi.CreatePrivateChat(userId, false)).thenApply(result -> {
            if(result instanceof TdApi.Error) {
                logger.error("Error creating user chat {}: {} ", userId, result);
                throw new IllegalStateException();
            }
            return (TdApi.Chat) result;
        });
    }

    public CompletableFuture<TdApi.Chat> searchPublicUsername(String username) {
        return client.send(new TdApi.SearchPublicChat(username)).thenApply(result -> (TdApi.Chat) result);
    }

    public CompletableFuture<TdApi.Message> sendMessage(long chatId, String text) {
        return client.send(new TdApi.SendMessage(chatId, 0, true, true, null,
                new TdApi.InputMessageText(new TdApi.FormattedText(text, null), false, true)))
                .thenApply(x -> (TdApi.Message)x);
    }

    public CompletableFuture<Optional<TdApi.Chat>> groupById(int supergroupId) {
        return client.send(new TdApi.CreateSupergroupChat(supergroupId, false)).thenApply(result -> {
            if(result instanceof TdApi.Error) {
                logger.info("Can't get group {}", result);
                return Optional.empty();
            }
            return Optional.of(((TdApi.Chat) result));
        });
    }

    public CompletableFuture<TdApi.Ok> deleteMessage(long chatId, boolean revoke, long ...messageIds) {
        return client.send(new TdApi.DeleteMessages(chatId, messageIds, revoke)).thenApply(ok -> (TdApi.Ok)ok);
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
