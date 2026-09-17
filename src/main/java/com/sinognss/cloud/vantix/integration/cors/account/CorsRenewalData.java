package com.sinognss.cloud.vantix.integration.cors.account;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/** The confirmed non-null data returned by CORS batch renewal. */
public record CorsRenewalData(
        @JsonProperty("interface_name") String interfaceName,
        @JsonProperty("corsNameList") List<String> corsNameList) {

    public CorsRenewalData {
        Objects.requireNonNull(interfaceName, "interface_name must not be null");
        Objects.requireNonNull(corsNameList, "corsNameList must not be null");
        corsNameList = List.copyOf(corsNameList);
    }
}
