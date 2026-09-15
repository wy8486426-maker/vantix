package com.sinognss.cloud.vantix.application.exchange;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class ExchangePayloadHash {
    private ExchangePayloadHash() { }

    static String calculate(ServiceCodeExchangeCommand command, Long effectiveAssignedUserId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, command.companyId() == null ? null : command.companyId().toString());
            update(digest, command.specCode());
            update(digest, command.generationSource() == null ? null : command.generationSource().name());
            update(digest, command.quantity() == null ? null : command.quantity().toString());
            update(digest, effectiveAssignedUserId == null ? null : effectiveAssignedUserId.toString());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
