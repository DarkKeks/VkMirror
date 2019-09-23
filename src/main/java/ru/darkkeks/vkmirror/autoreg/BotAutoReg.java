package ru.darkkeks.vkmirror.autoreg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.darkkeks.vkmirror.VkMirrorTelegram;
import ru.darkkeks.vkmirror.tdlib.TdApi;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class BotAutoReg {

    private static final Logger logger = LoggerFactory.getLogger(BotAutoReg.class);

    private static final Pattern timeoutPattern = Pattern.compile("Please try again in (\\d+) seconds.");

    private static final String MY_BOTS = "/mybots";
    private static final String BOT_FATHER = "BotFather";

    public static final int NO_TIMEOUT = -1;


    private VkMirrorTelegram account;
    private ScheduledExecutorService executor;

    private BlockingQueue<BotFatherAction<?>> actions;
    private BlockingQueue<TdApi.Message> responses;

    private TdApi.Chat botFatherChat;

    public BotAutoReg(VkMirrorTelegram account, ScheduledExecutorService executor) {
        this.account = account;
        this.executor = executor;

        this.actions = new LinkedBlockingQueue<>();
        this.responses = new LinkedBlockingQueue<>();

        account.onMessage(updateNewMessage -> {
            TdApi.Message message = updateNewMessage.message;
            if (message.chatId == botFatherChat.id && message.senderUserId != account.getMyId()) {
                responses.offer(message);
            }
        });

        account.searchPublicUsername(BOT_FATHER).thenAccept(chat -> {
            botFatherChat = chat;

            executor.submit(() -> {
                // It is here to reset state if it is messed up for some reason
                executeCommand(MY_BOTS);

                runActionFromQueue();
            });
        });
    }

    public String executeCommand(String command) {
        account.sendMessage(botFatherChat.id, command);
        try {
            // BotFather shouldn't reply with anything that is not text
            return ((TdApi.MessageText) responses.take().content).text.text;
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for " + command + " response");
            throw new IllegalStateException(e);
        }
    }

    private void runActionFromQueue() {
        try {
            BotFatherAction<?> action = actions.take();
            executor.submit(() -> {
                action.execute();
                runActionFromQueue();
            });
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for action", e);
        }
    }

    /**
     * @param response Response from BotFather supposedly containing timeout message
     * @return timeout in seconds, or {@value NO_TIMEOUT} is non is present
     */
    public int getTimeoutSeconds(String response) {
        Matcher matcher = timeoutPattern.matcher(response);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return NO_TIMEOUT;
    }

    /**
     * Creates new bot with specified name and username
     * @param username New bot username (without @), has to not be used
     * @param name New bot name
     * @return CompletableFuture with new bot token
     */
    public CompletableFuture<String> register(String username, String name) {
        RegisterAction action = new RegisterAction(this, username, name);
        actions.offer(action);
        return action.getCompletableFuture();
    }

    /**
     * Sets bot description
     * @param username Bot username (with @)
     * @param description New description
     */
    public CompletableFuture<Void> setDescription(String username, String description) {
        SetDescriptionAction action = new SetDescriptionAction(this, username, description);
        actions.offer(action);
        return action.getCompletableFuture();
    }

    // TODO BotAutoReg::setAvatar
    public CompletableFuture<Void> setAvatar(String username, Object avatar) {
        throw new UnsupportedOperationException();
    }

}
