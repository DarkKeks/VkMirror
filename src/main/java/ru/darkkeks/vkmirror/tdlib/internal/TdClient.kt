package ru.darkkeks.vkmirror.tdlib.internal

import kotlinx.coroutines.CompletableDeferred
import ru.darkkeks.vkmirror.util.NativeUtils
import ru.darkkeks.vkmirror.util.logger
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

    init {
        clientId = createNativeClient()
    }

    fun start() = thread(name = "TdClient thread") {
        run()
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
            logger.info("NativeClientSend\n$request")
            nativeClientSend(clientId, queryIdCounter.getAndIncrement(), request)
        }
    }

    /**
     * Sends request returning deferred response
     */
    suspend fun request(request: TdApi.Function): TdApi.Object = lock.read {
        val result = CompletableDeferred<TdApi.Object>()
        if (isClientDestroyed) {
            result.complete(TdApi.Error(500, "Client is closed"))
        } else {
            val requestId = queryIdCounter.getAndIncrement()
            requests[requestId] = result
            logger.info("NativeClientSend\n$request")
            nativeClientSend(clientId, requestId, request)
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
        val logger = logger()

        init {
            NativeUtils.loadLibraryFromJar("/libtdjni.so")

            execute(TdApi.SetLogStream(TdApi.LogStreamFile("tdlib.log", 1 shl 27)))
            execute(TdApi.SetLogVerbosityLevel(5))
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