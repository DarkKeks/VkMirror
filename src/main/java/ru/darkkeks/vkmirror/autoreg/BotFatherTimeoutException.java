package ru.darkkeks.vkmirror.autoreg;

public class BotFatherTimeoutException extends RuntimeException {
    private int seconds;

    public BotFatherTimeoutException(int seconds) {
        super(String.format("Encountered BotFather timeout: %d seconds", seconds));
        this.seconds = seconds;
    }

    public int getSeconds() {
        return seconds;
    }
}
