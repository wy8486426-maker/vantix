package com.sinognss.cloud.vantix.application.dashboard;

public record DashboardView(
        long serviceCodeTotal,
        long serviceCodeWaiting,
        long serviceCodeExpiring,
        long serviceCodeExpired,
        long serviceCodeProcessing,
        long serviceCodeConsumed,
        long accountTotal,
        long accountWaiting,
        long accountActive,
        long accountExpired,
        long accountDisabled,
        long generationOrderTotal,
        long exchangeTotal,
        long renewalTotal,
        long transferTotal) {
}
