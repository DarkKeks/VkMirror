//
// Copyright Aliaksei Levin (levlam@telegram.org), Arseny Smirnov (arseny30@gmail.com) 2014-2019
//
// Distributed under the Boost Software License, Version 1.0. (See accompanying
// file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)
//
package ru.darkkeks.vkmirror.tdlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.darkkeks.vkmirror.util.NativeUtils;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Main class for interaction with the TDLib.
 */
public final class Client implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Client.class);

    static {
        try {
            NativeUtils.loadLibraryFromJar("/libtdjni.so");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Interface for handler for results of queries to TDLib and incoming updates from TDLib.
     */
    public interface ResultHandler {
        /**
         * Callback called on result of query to TDLib or incoming update from TDLib.
         *
         * @param object Result of query or update of type TdApi.Update about new events.
         */
        void onResult(TdApi.Object object);
    }

    /**
     * Interface for handler of exceptions thrown while invoking ResultHandler.
     * By default, all such exceptions are ignored.
     * All exceptions thrown from ExceptionHandler are ignored.
     */
    public interface ExceptionHandler {
        /**
         * Callback called on exceptions thrown while invoking ResultHandler.
         *
         * @param e Exception thrown by ResultHandler.
         */
        void onException(Throwable e);
    }

    public CompletableFuture<TdApi.Object> send(TdApi.Function query) {
        Objects.requireNonNull(query, "query is null");

        CompletableFuture<TdApi.Object> future = new CompletableFuture<>();
        readLock.lock();
        try {
            if (isClientDestroyed) {
                future.complete(new TdApi.Error(500, "Client is closed"));
            } else {
                long queryId = currentQueryId.incrementAndGet();
                handlers.put(queryId, future);
                logger.info("NativeClientSend({}, {})\n{}", nativeClientId, queryId, query);
                nativeClientSend(nativeClientId, queryId, query);
            }
        } finally {
            readLock.unlock();
        }

        return future;
    }

    /**
     * Synchronously executes a TDLib request. Only a few marked accordingly requests can be executed synchronously.
     *
     * @param query Object representing a query to the TDLib.
     * @return request result.
     * @throws NullPointerException if query is null.
     */
    public static TdApi.Object execute(TdApi.Function query) {
        if (query == null) {
            throw new NullPointerException("query is null");
        }
        return nativeClientExecute(query);
    }

    /**
     * Overridden method from Runnable, do not call it directly.
     */
    @Override
    public void run() {
        while (!stopFlag) {
            receiveQueries(300.0 /*seconds*/);
        }
    }

    /**
     * Creates new Client.
     *
     * @param updatesHandler          Handler for incoming updates.
     * @param updatesExceptionHandler Handler for exceptions thrown from updatesHandler. If it is null, exceptions will be iggnored.
     * @return created Client
     */
    public static Client create(ResultHandler updatesHandler, ExceptionHandler updatesExceptionHandler) {
        Client client = new Client(updatesHandler, updatesExceptionHandler);
        new Thread(client, "TDLib thread").start();
        return client;
    }

    /**
     * Closes Client.
     */
    public void close() {
        writeLock.lock();
        try {
            if (isClientDestroyed) {
                return;
            }
            if (!stopFlag) {

                send(new TdApi.Close());
            }
            isClientDestroyed = true;
            while (!stopFlag) {
                Thread.yield();
            }
            while (handlers.size() != 1) {
                receiveQueries(300.0);
            }
            destroyNativeClient(nativeClientId);
        } finally {
            writeLock.unlock();
        }
    }

    private static final int MAX_EVENTS = 1000;

    private final Lock readLock;
    private final Lock writeLock;

    private volatile boolean stopFlag;
    private volatile boolean isClientDestroyed;
    private final long nativeClientId;

    private final ConcurrentHashMap<Long, CompletableFuture<TdApi.Object>> handlers;
    private final AtomicLong currentQueryId;

    private volatile ResultHandler resultHandler;
    private volatile ExceptionHandler exceptionHandler;

    private final long[] eventIds;
    private final TdApi.Object[] events;


    private Client(ResultHandler updatesHandler, ExceptionHandler updateExceptionHandler) {
        resultHandler = updatesHandler;
        exceptionHandler = updateExceptionHandler;

        nativeClientId = createNativeClient();
        handlers = new ConcurrentHashMap<>();
        currentQueryId = new AtomicLong();

        isClientDestroyed = stopFlag = false;

        events = new TdApi.Object[MAX_EVENTS];
        eventIds = new long[MAX_EVENTS];

        ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        writeLock = readWriteLock.writeLock();
        readLock = readWriteLock.readLock();

    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    private void processResult(long id, TdApi.Object object) {
        if (object instanceof TdApi.UpdateAuthorizationState) {
            if (((TdApi.UpdateAuthorizationState) object).authorizationState instanceof TdApi.AuthorizationStateClosed) {
                stopFlag = true;
            }
        }

        if (id == 0) {
            // update handler stays forever
            try {
                resultHandler.onResult(object);
            } catch (Throwable cause) {
                if (exceptionHandler != null) {
                    try {
                        exceptionHandler.onException(cause);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } else if(handlers.containsKey(id)) {
            ForkJoinPool.commonPool().submit(() -> {
                logger.info("{}", object);
                handlers.get(id).complete(object);
            });
        }
    }

    private void receiveQueries(double timeout) {
        int resultN = nativeClientReceive(nativeClientId, eventIds, events, timeout);
        for (int i = 0; i < resultN; i++) {
            processResult(eventIds[i], events[i]);
            events[i] = null;
        }
    }

    private static native long createNativeClient();

    private static native void nativeClientSend(long nativeClientId, long eventId, TdApi.Function function);

    private static native int nativeClientReceive(long nativeClientId, long[] eventIds, TdApi.Object[] events, double timeout);

    private static native TdApi.Object nativeClientExecute(TdApi.Function function);

    private static native void destroyNativeClient(long nativeClientId);
}
