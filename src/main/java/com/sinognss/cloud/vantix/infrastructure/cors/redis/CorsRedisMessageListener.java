package com.sinognss.cloud.vantix.infrastructure.cors.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinognss.cloud.vantix.application.cors.account.CorsRealtimeRefreshCoordinator;
import com.sinognss.cloud.vantix.integration.cors.redis.CorsAccountStatusNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class CorsRedisMessageListener implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(CorsRedisMessageListener.class);
    private static final int MAX_PAYLOAD_BYTES = 4096;
    private static final Set<String> KNOWN_ACTIONS = Set.of("active", "expire", "disable", "updatePass");
    private final ObjectMapper objectMapper;
    private final CorsRealtimeRefreshCoordinator coordinator;

    public CorsRedisMessageListener(ObjectMapper objectMapper, CorsRealtimeRefreshCoordinator coordinator) {
        this.objectMapper = objectMapper;
        this.coordinator = coordinator;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        byte[] body = message == null ? null : message.getBody();
        if (body == null || body.length == 0 || body.length > MAX_PAYLOAD_BYTES) {
            log.warn("CORS Redis notification ignored; errorCode=INVALID_CORS_NOTIFICATION_PAYLOAD");
            return;
        }
        CorsAccountStatusNotification notification;
        try {
            notification = objectMapper.readValue(body, CorsAccountStatusNotification.class);
        } catch (Exception malformed) {
            log.warn("CORS Redis notification ignored; errorCode=INVALID_CORS_NOTIFICATION_JSON");
            return;
        }
        if (notification.userName() == null || notification.userName().isBlank()
                || notification.userName().length() > 255 || notification.action() == null
                || notification.action().isBlank() || notification.action().length() > 64) {
            log.warn("CORS Redis notification ignored; errorCode=INVALID_CORS_NOTIFICATION_FIELDS");
            return;
        }
        if (!KNOWN_ACTIONS.contains(notification.action())) {
            log.warn("CORS Redis notification has unknown action; action={} errorCode=UNKNOWN_CORS_ACCOUNT_ACTION",
                    notification.action());
        }
        coordinator.accept(notification.userName(), notification.action());
    }
}
