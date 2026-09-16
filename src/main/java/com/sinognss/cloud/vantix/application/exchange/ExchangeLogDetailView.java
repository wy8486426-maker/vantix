package com.sinognss.cloud.vantix.application.exchange;

import java.util.List;

public record ExchangeLogDetailView(ExchangeLogView batch, List<ExchangeLogItemView> items) {
    public ExchangeLogDetailView {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
