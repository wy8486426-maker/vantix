package com.sinognss.cloud.vantix.integration.cors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Data returned by CORS CommonResult for /userInfo/add. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CorsAddAccountData(
        @JsonProperty("interface_name") String interfaceName,
        @JsonProperty("corsNameList") List<String> corsNameList) {

    @JsonCreator
    public CorsAddAccountData {
        corsNameList = corsNameList == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(corsNameList));
    }

    public boolean hasValidCorsNameList(int expectedSize) {
        if (corsNameList == null || corsNameList.size() != expectedSize) {
            return false;
        }
        Set<String> names = new HashSet<>();
        return corsNameList.stream().allMatch(name -> name != null
                && !name.isBlank() && names.add(name));
    }
}
