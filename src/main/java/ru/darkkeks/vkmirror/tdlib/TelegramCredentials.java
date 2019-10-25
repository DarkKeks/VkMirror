package ru.darkkeks.vkmirror.tdlib;

public class TelegramCredentials {

    private int apiId;
    private String apiHash;

    private boolean isBot;
    private String phoneNumber;
    private String botToken;

    private TelegramCredentials(int apiId, String apiHash, String phoneNumber, String botToken, boolean isBot) {
        this.apiId = apiId;
        this.apiHash = apiHash;
        this.phoneNumber = phoneNumber;
        this.botToken = botToken;
        this.isBot = isBot;
    }

    public static TelegramCredentials phone(int apiId, String apiHash, String phoneNumber) {
        return new TelegramCredentials(apiId, apiHash, phoneNumber, null, false);
    }

    public static TelegramCredentials bot(int apiId, String apiHash, String botToken) {
        return new TelegramCredentials(apiId, apiHash, null, botToken, true);
    }

    public TdApi.Function getCredentialsFunction() {
        if (isBot) {
            return new TdApi.CheckAuthenticationBotToken(botToken);
        } else {
            return new TdApi.SetAuthenticationPhoneNumber(phoneNumber, false, false);
        }
    }

    public String getDataDirectory() {
        return "tdlib" + (isBot ? "-" + botToken.substring(0, botToken.indexOf(":")) : phoneNumber);
    }

    public int getApiId() {
        return apiId;
    }

    public String getApiHash() {
        return apiHash;
    }
}
