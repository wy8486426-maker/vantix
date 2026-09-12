package com.sinognss.cloud.vantix.application.offline;

import java.util.List;

public record OfflineImportFailure(boolean success, List<OfflineImportError> errors) {
}