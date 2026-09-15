package com.sinognss.cloud.vantix.application.servicecode;

import java.util.List;

public record TransferDetailView(TransferBatchView batch, List<TransferItemView> items) {
    public TransferDetailView {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
