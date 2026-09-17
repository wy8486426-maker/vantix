package com.sinognss.cloud.vantix.integration.cors;

/** CORS /BaseUser/userInfo/add request built from the frozen exchange batch snapshot. */
public record CorsBatchCreateRequest(String requestId, int addNum, int accountType,
                                     int durationType, String accountName, int nameType,
                                     int silenceType, int activeType, String remark,
                                     Long dealerId, int normalType) {
    public CorsBatchCreateRequest {
        if (requestId == null || requestId.isBlank() || requestId.length() > 128
                || requestId.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("requestId must be non-blank and at most 128 characters");
        }
        if (addNum <= 0) {
            throw new IllegalArgumentException("addNum must be positive");
        }
        if (accountType != 0 || nameType != 0 || activeType != 1 || normalType != 0) {
            throw new IllegalArgumentException("unsupported CORS account creation type");
        }
        if (durationType <= 0) {
            throw new IllegalArgumentException("durationType must be positive");
        }
        if (silenceType < 0) {
            throw new IllegalArgumentException("silenceType must not be negative");
        }
        if (accountName == null || accountName.isBlank() || accountName.length() > 64
                || accountName.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("accountName is invalid");
        }
        if (dealerId == null || dealerId <= 0) {
            throw new IllegalArgumentException("dealerId must be positive");
        }
        if (remark != null && (remark.length() > 1024
                || remark.codePoints().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("remark is invalid");
        }
    }

}
