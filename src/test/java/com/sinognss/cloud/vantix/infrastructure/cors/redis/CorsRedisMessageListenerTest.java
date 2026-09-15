package com.sinognss.cloud.vantix.infrastructure.cors.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinognss.cloud.vantix.application.cors.account.CorsAccountRealtimeRefreshService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CorsRedisMessageListenerTest {
    private final CorsAccountRealtimeRefreshService refreshService = mock(CorsAccountRealtimeRefreshService.class);
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(1, 1, 1,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    private final CorsRedisMessageListener listener = new CorsRedisMessageListener(
            new ObjectMapper(), refreshService, workers);

    @AfterEach
    void close() { workers.shutdownNow(); }

    @Test
    void validKnownAndFutureActionsTriggerRefreshWithoutPayloadLogging() throws Exception {
        listener.onMessage(new DefaultMessage("channel".getBytes(StandardCharsets.UTF_8),
                "{\"userName\":\"account001\",\"action\":\"updatePass\"}"
                        .getBytes(StandardCharsets.UTF_8)), null);
        listener.onMessage(new DefaultMessage("channel".getBytes(StandardCharsets.UTF_8),
                "{\"userName\":\"account001\",\"action\":\"futureAction\"}"
                        .getBytes(StandardCharsets.UTF_8)), null);
        workers.shutdown();
        workers.awaitTermination(2, TimeUnit.SECONDS);
        verify(refreshService).refresh(eq("account001"), eq("updatePass"));
        verify(refreshService).refresh(eq("account001"), eq("futureAction"));
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
}
