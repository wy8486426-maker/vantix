package com.sinognss.cloud.vantix.infrastructure.cors.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountRealtimeRefreshService;
import com.sinognss.cloud.vantix.application.cors.account.CorsRealtimeRefreshCoordinator;
import com.sinognss.cloud.vantix.config.CorsRedisProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CorsRedisMessageListenerTest {
    private final CorsAccountRealtimeRefreshService refreshService = mock(CorsAccountRealtimeRefreshService.class);
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(1, 1, 1,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    private final ScheduledThreadPoolExecutor confirmations = new ScheduledThreadPoolExecutor(1);
    private final CorsRedisProperties properties = properties();
    private final CorsRealtimeRefreshCoordinator coordinator = new CorsRealtimeRefreshCoordinator(
            refreshService, workers, confirmations, properties);
    private final CorsRedisMessageListener listener = new CorsRedisMessageListener(
            new ObjectMapper(), coordinator);

    @AfterEach
    void close() { workers.shutdownNow(); confirmations.shutdownNow(); }

    @Test
    void validKnownAndFutureActionsTriggerRefreshWithoutPayloadLogging() throws Exception {
        listener.onMessage(new DefaultMessage("channel".getBytes(StandardCharsets.UTF_8),
                "{\"userName\":\"account001\",\"action\":\"updatePass\"}"
                        .getBytes(StandardCharsets.UTF_8)), null);
        listener.onMessage(new DefaultMessage("channel".getBytes(StandardCharsets.UTF_8),
                "{\"userName\":\"account001\",\"action\":\"futureAction\"}"
                        .getBytes(StandardCharsets.UTF_8)), null);
        verify(refreshService, timeout(2000).times(2)).refresh(eq("account001"), eq("updatePass"));
        verify(refreshService, timeout(2000).times(2)).refresh(eq("account001"), eq("futureAction"));
    }

    @Test
    void malformedAndOversizedPayloadsAreIgnored() throws Exception {
        listener.onMessage(new DefaultMessage("channel".getBytes(StandardCharsets.UTF_8),
                "{}".getBytes(StandardCharsets.UTF_8)), null);
        listener.onMessage(new DefaultMessage("channel".getBytes(StandardCharsets.UTF_8),
                "not-json".getBytes(StandardCharsets.UTF_8)), null);
        listener.onMessage(new DefaultMessage("channel".getBytes(StandardCharsets.UTF_8),
                new byte[4097]), null);
        Thread.sleep(100);
        verifyNoInteractions(refreshService);
    }

    private static CorsRedisProperties properties() {
        CorsRedisProperties result = new CorsRedisProperties();
        result.setConfirmationDelay(java.time.Duration.ZERO);
        result.setQueueCapacity(10);
        return result;
    }
}
