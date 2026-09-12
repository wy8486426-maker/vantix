package com.sinognss.cloud.vantix.application.offline;

public record OfflineImportError(int row, String field, String message) {
}