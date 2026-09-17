package com.sinognss.cloud.vantix.application.history;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

final class HistoryImportPayloadHash {
    private HistoryImportPayloadHash() { }

    static String calculate(HistoryAccountImportCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, command.companyId().toString());
            update(digest, command.specCode());
            command.accounts().stream()
                    .sorted(Comparator.comparing(HistoryAccountIdentity::id)
                            .thenComparing(HistoryAccountIdentity::name))
                    .forEach(item -> {
                        update(digest, item.id().toString());
                        update(digest, item.name());
                    });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
