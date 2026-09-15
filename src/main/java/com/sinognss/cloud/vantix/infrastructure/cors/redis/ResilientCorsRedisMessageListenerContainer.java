package com.sinognss.cloud.vantix.infrastructure.cors.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ResilientCorsRedisMessageListenerContainer extends RedisMessageListenerContainer {
    private static final Logger log = LoggerFactory.getLogger(ResilientCorsRedisMessageListenerContainer.class);
    private final ScheduledExecutorService recoveryExecutor;
    private final long recoveryIntervalMillis;
    private final AtomicBoolean stopping = new AtomicBoolean();
    private ScheduledFuture<?> retry;

    public ResilientCorsRedisMessageListenerContainer(long recoveryIntervalMillis) {
        this.recoveryIntervalMillis = recoveryIntervalMillis;
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(runnable, "cors-redis-initial-recovery");
            thread.setDaemon(true);
            return thread;
        };
        this.recoveryExecutor = Executors.newSingleThreadScheduledExecutor(threads);
    }

    @Override
    public synchronized void start() {
        if (stopping.get() || isRunning()) return;
        try {
            super.start();
            if (retry != null) retry.cancel(false);
            retry = null;
        } catch (RuntimeException unavailable) {
            log.warn("CORS Redis subscription unavailable; will retry in {} ms; errorCode=REDIS_UNAVAILABLE",
                    recoveryIntervalMillis);
            scheduleRetry();
        }
    }

    private synchronized void scheduleRetry() {
        if (stopping.get() || (retry != null && !retry.isDone())) return;
        retry = recoveryExecutor.schedule(this::start, recoveryIntervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        stopping.set(true);
        if (retry != null) retry.cancel(false);
        recoveryExecutor.shutdownNow();
        super.stop();
    }
}
