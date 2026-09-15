package com.sinognss.cloud.vantix.application.cors.account;

import com.sinognss.cloud.vantix.config.CorsRedisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class CorsRealtimeRefreshCoordinator {
    private static final Logger log = LoggerFactory.getLogger(CorsRealtimeRefreshCoordinator.class);
    private final CorsAccountRealtimeRefreshService refreshService;
    private final ThreadPoolExecutor workers;
    private final ScheduledThreadPoolExecutor confirmationScheduler;
    private final long confirmationDelayMillis;
    private final int confirmationLimit;
    private final AtomicInteger pendingConfirmations = new AtomicInteger();

    public CorsRealtimeRefreshCoordinator(CorsAccountRealtimeRefreshService refreshService,
                                          ThreadPoolExecutor workers,
                                          ScheduledThreadPoolExecutor confirmationScheduler,
                                          CorsRedisProperties properties) {
        this.refreshService = refreshService;
        this.workers = workers;
        this.confirmationScheduler = confirmationScheduler;
        this.confirmationDelayMillis = properties.getConfirmationDelay().toMillis();
        this.confirmationLimit = properties.getQueueCapacity();
    }

    public void accept(String userName, String action) {
        try {
            workers.execute(() -> refreshService.refresh(userName, action));
        } catch (java.util.concurrent.RejectedExecutionException full) {
            log.warn("CORS Redis notification dropped; action={} errorCode=REALTIME_REFRESH_QUEUE_FULL", action);
        }
        scheduleConfirmation(userName, action);
    }

    private void scheduleConfirmation(String userName, String action) {
        int pending = pendingConfirmations.incrementAndGet();
        if (pending > confirmationLimit) {
            pendingConfirmations.decrementAndGet();
            log.warn("CORS Redis confirmation dropped; action={} errorCode=REALTIME_CONFIRMATION_QUEUE_FULL", action);
            return;
        }
        try {
            confirmationScheduler.schedule(() -> {
                pendingConfirmations.decrementAndGet();
                try {
                    workers.execute(() -> refreshService.refresh(userName, action));
                } catch (java.util.concurrent.RejectedExecutionException full) {
                    log.warn("CORS Redis confirmation dropped; action={} errorCode=REALTIME_REFRESH_QUEUE_FULL", action);
                }
            }, confirmationDelayMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException rejected) {
            pendingConfirmations.decrementAndGet();
            log.warn("CORS Redis confirmation dropped; action={} errorCode=REALTIME_CONFIRMATION_QUEUE_FULL", action);
        }
    }
}
