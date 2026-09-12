package com.sinognss.cloud.vantix.application.offline;

import java.util.List;

public record OfflineParseResult(List<ParsedOfflineOrder> rows, List<OfflineImportError> errors) {
}