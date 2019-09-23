package ru.darkkeks.vkmirror.autoreg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class RegisterAction extends BotFatherAction<String> {

    private static final Logger logger = LoggerFactory.getLogger(RegisterAction.class);

    private final Pattern tokenPattern = Pattern.compile("\\d+:\\w+");
    private final Pattern alreadyTaken = Pattern.compile("already taken");

    public static final String NEW_BOT = "/newbot";

    private String username;
    private String name;

    public RegisterAction(BotAutoReg autoReg, String username, String name) {
        super(autoReg);
        this.username = username;
        this.name = name;
    }

    @Override
    public void execute() {
        String newBot = getAutoReg().executeCommand(NEW_BOT);

        int timeout = getAutoReg().getTimeoutSeconds(newBot);
        if (timeout != BotAutoReg.NO_TIMEOUT) {
            getCompletableFuture().completeExceptionally(new BotFatherTimeoutException(timeout));
            return;
        }

        String afterName = getAutoReg().executeCommand(name);
        String afterUsername = getAutoReg().executeCommand(username);

        if (alreadyTaken.matcher(afterUsername).find()) {
            getCompletableFuture().completeExceptionally(
                    new IllegalStateException(String.format("Bot username %s is already taken", username)));
            return;
        }

        Matcher matcher = tokenPattern.matcher(afterUsername);
        boolean foundToken = matcher.find();
        if (foundToken) {
            String token = matcher.group(0);
            finish(token);
        } else {
            logger.error("No token in BotFather response!:\n{}", afterUsername);
            getCompletableFuture().completeExceptionally(new IllegalStateException("No token in response"));
        }
    }
}
