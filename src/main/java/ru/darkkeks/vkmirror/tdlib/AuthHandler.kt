package ru.darkkeks.vkmirror.tdlib

import ru.darkkeks.vkmirror.tdlib.internal.TdApi
import ru.darkkeks.vkmirror.tdlib.internal.TdClient
import ru.darkkeks.vkmirror.util.EventHandler
import ru.darkkeks.vkmirror.util.prompt
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class AuthHandler(val credentials: TelegramCredentials, val client: TdClient) {

    private var authorized = false

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    private val updateHandler = EventHandler<TdApi.AuthorizationState>()

    init {
        updateHandler.apply {
            addHandler(TdApi.AuthorizationStateWaitTdlibParameters::class) {
                client.send(TdApi.SetTdlibParameters(createParameters()))
            }

            addHandler(TdApi.AuthorizationStateWaitEncryptionKey::class) {
                client.send(TdApi.CheckDatabaseEncryptionKey())
            }

            addHandler(TdApi.AuthorizationStateWaitPhoneNumber::class) {
                client.send(credentials.credentialsFunction)
            }

            addHandler(TdApi.AuthorizationStateWaitCode::class) {
                client.send(TdApi.CheckAuthenticationCode(prompt("Please enter authentication code"), "", ""))
            }

            addHandler(TdApi.AuthorizationStateWaitPassword::class) {
                client.send(TdApi.CheckAuthenticationPassword(prompt("Please enter password")))
            }

            addHandler(TdApi.AuthorizationStateReady::class) {
                lock.withLock {
                    authorized = true
                    condition.signalAll()
                }
            }
        }
    }

    fun onAuthorizationStateChange(update: TdApi.UpdateAuthorizationState) =
            updateHandler.onMessage(update.authorizationState)

    fun createParameters() = TdApi.TdlibParameters().apply {
        databaseDirectory = credentials.dataDirectory
        enableStorageOptimizer = true
        useMessageDatabase = true
        apiId = credentials.apiId
        apiHash = credentials.apiHash
        systemLanguageCode = "en"
        deviceModel = "Desktop"
        systemVersion = "Unknown"
        applicationVersion = "1.0"
    }

    fun awaitAuthorization() = lock.withLock {
        while (!authorized) {
            condition.await()
        }
    }
}