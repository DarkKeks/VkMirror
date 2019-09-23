package ru.darkkeks.vkmirror.tdlib;

import ru.darkkeks.vkmirror.Config;
import ru.darkkeks.vkmirror.VkMirrorTelegram;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class AuthHandler implements Consumer<TdApi.UpdateAuthorizationState> {

    private Lock authLock = new ReentrantLock();
    private Condition authCondition = authLock.newCondition();
    private boolean authorized;

    private VkMirrorTelegram telegram;

    private int apiId;
    private String apiHash;

    private String phoneNumber;
    private String botToken;

    private TdApi.AuthorizationState state;

    private AuthHandler(VkMirrorTelegram telegram, int apiId, String apiHash) {
        this.telegram = telegram;
        this.apiId = apiId;
        this.apiHash = apiHash;
        this.authorized = false;
    }

    public static AuthHandler phone(VkMirrorTelegram telegram, int apiId, String apiHash, String phoneNumber) {
        AuthHandler result = new AuthHandler(telegram, apiId, apiHash);
        result.phoneNumber = phoneNumber;
        return result;
    }

    public static AuthHandler bot(VkMirrorTelegram telegram, int apiId, String apiHash, String botToken) {
        AuthHandler result = new AuthHandler(telegram, apiId, apiHash);
        result.botToken = botToken;
        return result;
    }

    @Override
    public void accept(TdApi.UpdateAuthorizationState updateAuthorizationState) {
        TdApi.AuthorizationState authState = updateAuthorizationState.authorizationState;
        if(authState != null) {
            state = authState;
        }
        switch (state.getConstructor()) {
            case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR: {
                sendParameters();
                break;
            }
            case TdApi.AuthorizationStateWaitEncryptionKey.CONSTRUCTOR: {
                telegram.getClient().send(new TdApi.CheckDatabaseEncryptionKey());
                break;
            }
            case TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR: {
                if(phoneNumber != null) {
                    telegram.getClient().send(new TdApi.SetAuthenticationPhoneNumber(phoneNumber, false, false));
                } else {
                    telegram.getClient().send(new TdApi.CheckAuthenticationBotToken(botToken));
                }
                break;
            }
            case TdApi.AuthorizationStateWaitCode.CONSTRUCTOR: {
                String code = promptString("Please enter authentication code: ");
                telegram.getClient().send(new TdApi.CheckAuthenticationCode(code, "", ""));
                break;
            }
            case TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR: {
                String password = promptString("Please enter password: ");
                telegram.getClient().send(new TdApi.CheckAuthenticationPassword(password));
                break;
            }
            case TdApi.AuthorizationStateReady.CONSTRUCTOR:
                authorized = true;
                authLock.lock();
                try {
                    authCondition.signalAll();
                } finally {
                    authLock.unlock();
                }
                break;
            case TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR:
                // Logging out
                break;
            case TdApi.AuthorizationStateClosing.CONSTRUCTOR:
                // Closing
                break;
            case TdApi.AuthorizationStateClosed.CONSTRUCTOR:
                // Closed
                break;
        }
    }

    public void awaitAuthorization() {
        authLock.lock();
        try {
            while (!authorized) {
                authCondition.await();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            authLock.unlock();
        }
    }

    private void sendParameters() {
        TdApi.TdlibParameters parameters = new TdApi.TdlibParameters();
        parameters.databaseDirectory = phoneNumber != null ? "tdlib" + phoneNumber : "tdlib" + botToken;
        parameters.enableStorageOptimizer = true;
        parameters.useMessageDatabase = true;
        parameters.useSecretChats = true;
        parameters.apiId = apiId;
        parameters.apiHash = apiHash;
        parameters.systemLanguageCode = "en";
        parameters.deviceModel = "Desktop";
        parameters.systemVersion = "Unknown";
        parameters.applicationVersion = "1.0";

        telegram.getClient().send(new TdApi.SetTdlibParameters(parameters));
    }


    private static String promptString(String prompt) {
        System.out.print(prompt);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String str = "";
        try {
            str = reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return str;
    }
}


