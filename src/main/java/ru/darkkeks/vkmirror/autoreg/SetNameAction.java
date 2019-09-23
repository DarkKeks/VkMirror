package ru.darkkeks.vkmirror.autoreg;

// TODO Extract common class with SetDescription ?
//  May be should look into this after implementing bot avatar
public class SetNameAction extends BotFatherAction<Void> {

    private static final String SET_NAME = "/setname";

    private String username;
    private String name;

    public SetNameAction(BotAutoReg autoReg, String username, String name) {
        super(autoReg);
        this.username = username;
        this.name = name;
    }

    @Override
    public void execute() {
        String newBot = getAutoReg().executeCommand(SET_NAME);

        int timeout = getAutoReg().getTimeoutSeconds(newBot);
        if (timeout != BotAutoReg.NO_TIMEOUT) {
            getCompletableFuture().completeExceptionally(new BotFatherTimeoutException(timeout));
            return;
        }

        String afterUsername = getAutoReg().executeCommand(username);

        if(BotAutoReg.INVALID_BOT_SELECTED.matcher(afterUsername).find()) {
            getCompletableFuture().completeExceptionally(new IllegalStateException("Invalid username"));
        }

        String result = getAutoReg().executeCommand(name);

        finish(null);
    }
}
