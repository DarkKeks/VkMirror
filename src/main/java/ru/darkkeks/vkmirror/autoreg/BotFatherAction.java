package ru.darkkeks.vkmirror.autoreg;

import java.util.concurrent.CompletableFuture;

abstract class BotFatherAction<T> {
    private CompletableFuture<T> callback;
    private BotAutoReg autoReg;

    public BotFatherAction(BotAutoReg autoReg) {
        this.autoReg = autoReg;
        this.callback = new CompletableFuture<>();
    }

    public CompletableFuture<T> getCompletableFuture() {
        return callback;
    }

    public abstract void execute();

    protected void finish(T result) {
        callback.complete(result);
    }

    public BotAutoReg getAutoReg() {
        return autoReg;
    }
}
