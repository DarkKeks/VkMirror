package ru.darkkeks.vkmirror.tdlib.internal

import kotlinx.coroutines.CompletableDeferred
import ru.darkkeks.vkmirror.API_HASH
import ru.darkkeks.vkmirror.API_ID
import ru.darkkeks.vkmirror.PHONE_NUMBER
import ru.darkkeks.vkmirror.tdlib.userTelegramCredentials
import ru.darkkeks.vkmirror.util.NativeUtils
import ru.darkkeks.vkmirror.util.createLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.thread
import kotlin.concurrent.write

internal const val MAX_EVENTS = 1000
internal const val DEFAULT_TIMEOUT = 300.0

class TdClient(val updateHandler: (TdApi.Object) -> Unit,
               val exceptionHandler: (Throwable) -> Unit) {

    private val clientId: Long

    private var stopped = false

    private val requests = ConcurrentHashMap<Long, CompletableDeferred<TdApi.Object>>()

    private val eventIds = LongArray(MAX_EVENTS)
    private val events = Array<TdApi.Object?>(MAX_EVENTS) { null }

    private val lock = ReentrantReadWriteLock()

    private var isClientDestroyed = false
    private val queryIdCounter = AtomicLong(1)

    private val thread: Thread

    init {
        clientId = createNativeClient()
        thread = thread(start = false, name = "TdClient thread") {
            run()
        }
    }

    fun start() {
        synchronized(thread) {
            if (!thread.isAlive) {
                thread.start()
            }
        }
    }

    private fun run() {
        while (!stopped) {
            receiveQueries(DEFAULT_TIMEOUT)
        }
    }

    /**
     * Sends request without returning result
     */
    fun send(request: TdApi.Function) = lock.read {
        if (!isClientDestroyed) {
            val requestId = queryIdCounter.getAndIncrement()
            println("send -- nativeClientSend\n$request")
            nativeClientSend(clientId, requestId, request)
        }
    }

    /**
     * Sends request returning deferred response
     */
    suspend fun request(request: TdApi.Function): TdApi.Object {
        val result = CompletableDeferred<TdApi.Object>()
        lock.read {
            if (isClientDestroyed) {
                result.complete(TdApi.Error(500, "Client is closed"))
            } else {
                val requestId = queryIdCounter.getAndIncrement()
                requests[requestId] = result
                println("request -- nativeClientSend $requestId \n$request")
                nativeClientSend(clientId, requestId, request)
            }
        }
        return result.await()
    }

    private fun processResult(id: Long, tdObject: TdApi.Object) {
        if (tdObject is TdApi.UpdateAuthorizationState &&
                tdObject.authorizationState is TdApi.AuthorizationStateClosed) {
            stopped = true
        }

        if (id == 0L) {
            try {
                updateHandler(tdObject)
            } catch (cause: Throwable) {
                exceptionHandler(cause)
            }
        } else if (requests.containsKey(id)) {
            requests.remove(id)?.complete(tdObject)
        }
    }

    private fun receiveQueries(timeout: Double) {
        val count = nativeClientReceive(clientId, eventIds, events, timeout)
        repeat(count) {
            processResult(eventIds[it], events[it]!!)
            events[it] = null
        }
    }

    fun close() = lock.write {
        if (isClientDestroyed) {
            return
        }
        if (!stopped) {
            send(TdApi.Close())
        }
        isClientDestroyed = true
        while (!stopped) {
            Thread.yield()
        }
        while (requests.size != 1) {
            receiveQueries(DEFAULT_TIMEOUT)
        }
        destroyNativeClient(clientId)
    }

    companion object {
        val logger = createLogger<TdClient>()

        init {
            NativeUtils.loadLibraryFromJar("/libtdjni.so")

//            execute(TdApi.SetLogStream(TdApi.LogStreamFile("tdlib.log", 1 shl 27)))
            execute(TdApi.SetLogVerbosityLevel(2))
        }

        @JvmStatic
        external fun createNativeClient(): Long

        @JvmStatic
        external fun nativeClientSend(clientId: Long, id: Long, function: TdApi.Function)

        @JvmStatic
        external fun nativeClientReceive(clientId: Long, ids: LongArray, events: Array<TdApi.Object?>, timeout: Double): Int

        @JvmStatic
        external fun destroyNativeClient(clientId: Long)

        @JvmStatic
        external fun nativeClientExecute(function: TdApi.Function): TdApi.Object

        fun execute(function: TdApi.Function) {
            nativeClientExecute(function)
        }
    }
}

fun main() {
    val id = TdClient.createNativeClient()

    val events = Array<TdApi.Object?>(1000) { null }
    val eventIds = LongArray(1000)

    val credentials = userTelegramCredentials(API_ID, API_HASH, PHONE_NUMBER)

    var reqId = 1L;

    val send : (TdApi.Function) -> Unit = {
        println("Sending with id $reqId $it")
        TdClient.nativeClientSend(id, reqId++, it)
    }

    send(TdApi.DisableProxy())

    while (true) {
        val count = TdClient.nativeClientReceive(id, eventIds, events, 300.0)
        repeat(count) {
            val event = events[it]
            events[it] = null

            println("Received $event")

            when (event) {
                is TdApi.UpdateAuthorizationState -> when (event.authorizationState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> {
                        send(TdApi.SetTdlibParameters(TdApi.TdlibParameters().apply {
                            databaseDirectory = credentials.dataDirectory
                            enableStorageOptimizer = true
                            useMessageDatabase = true
                            apiId = credentials.apiId
                            apiHash = credentials.apiHash
                            systemLanguageCode = "en"
                            deviceModel = "Desktop"
                            systemVersion = "Unknown"
                            applicationVersion = "1.0"
                        }))
                    }

                    is TdApi.AuthorizationStateWaitEncryptionKey -> {
                        send(TdApi.CheckDatabaseEncryptionKey())
                    }

                    is TdApi.AuthorizationStateWaitPhoneNumber -> {
                        send(credentials.credentialsFunction)
                    }

                    is TdApi.AuthorizationStateWaitCode -> {
                        send
                    }

                    is TdApi.AuthorizationStateReady -> {
                        send(TdApi.SearchPublicChat("BotFather"))
                    }
                }
            }
        }
    }
}