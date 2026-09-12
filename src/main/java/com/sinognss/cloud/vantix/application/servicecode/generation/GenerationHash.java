package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class GenerationHash {
    private GenerationHash() { }

    static String businessKey(GenerationSource source, Long companyId, String orderNo, String specCode) {
        return sha256(source.name(), companyId.toString(), orderNo, specCode);
    }

    static String sha256(String... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String field : fields) {
                if (field == null) {
                    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
                    continue;
                }
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static String childRequestId(String orderRequestId, String specCode) {
        return "BATCH:" + sha256(orderRequestId, specCode);
    }
}
