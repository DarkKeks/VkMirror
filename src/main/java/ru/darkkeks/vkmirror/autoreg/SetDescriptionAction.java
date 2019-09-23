package ru.darkkeks.vkmirror.autoreg;

public class SetDescriptionAction extends BotFatherAction<Void> {

    private static final String SET_DESCRIPTION = "/setdescription";

    private String username;
    private String description;

    public SetDescriptionAction(BotAutoReg autoReg, String username, String description) {
        super(autoReg);

        this.username = username;
        this.description = description;
    }

    @Override
    public void execute() {
        String newBot = getAutoReg().executeCommand(SET_DESCRIPTION);

        int timeout = getAutoReg().getTimeoutSeconds(newBot);
        if (timeout != BotAutoReg.NO_TIMEOUT) {
            getCompletableFuture().completeExceptionally(new BotFatherTimeoutException(timeout));
            return;
        }

        String afterUsername = getAutoReg().executeCommand(username);

        if(BotAutoReg.INVALID_BOT_SELECTED.matcher(afterUsername).find()) {
            getCompletableFuture().completeExceptionally(new IllegalStateException("Invalid username"));
        }

        String result = getAutoReg().executeCommand(description);

        finish(null);
    }
}
