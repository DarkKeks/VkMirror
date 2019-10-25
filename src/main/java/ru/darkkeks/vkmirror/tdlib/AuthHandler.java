package ru.darkkeks.vkmirror.tdlib;

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

    private TelegramClient telegram;
    private TelegramCredentials credentials;

    private TdApi.AuthorizationState state;

    public AuthHandler(TelegramClient telegram, TelegramCredentials credentials) {
        this.telegram = telegram;
        this.credentials = credentials;
        this.authorized = false;
    }

    @Override
    public void accept(TdApi.UpdateAuthorizationState updateAuthorizationState) {
        TdApi.AuthorizationState authState = updateAuthorizationState.authorizationState;
        assert authState != null; // FIXME
        if(authState != null) {
            state = authState;
        }
        switch (state.getConstructor()) {
            case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR: {
                telegram.getClient().send(createParameters());
                break;
            }
            case TdApi.AuthorizationStateWaitEncryptionKey.CONSTRUCTOR: {
                telegram.getClient().send(new TdApi.CheckDatabaseEncryptionKey());
                break;
            }
            case TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR: {
                telegram.getClient().send(credentials.getCredentialsFunction());
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

    private TdApi.SetTdlibParameters createParameters() {
        TdApi.TdlibParameters parameters = new TdApi.TdlibParameters();
        parameters.databaseDirectory = credentials.getDataDirectory();
        parameters.enableStorageOptimizer = true;
        parameters.useMessageDatabase = true;
        parameters.useSecretChats = true;
        parameters.apiId = credentials.getApiId();
        parameters.apiHash = credentials.getApiHash();
        parameters.systemLanguageCode = "en";
        parameters.deviceModel = "Desktop";
        parameters.systemVersion = "Unknown";
        parameters.applicationVersion = "1.0";
        return new TdApi.SetTdlibParameters(parameters);
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


