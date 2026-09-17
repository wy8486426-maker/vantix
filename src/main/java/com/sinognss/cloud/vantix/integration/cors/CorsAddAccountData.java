package com.sinognss.cloud.vantix.integration.cors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Data returned by CORS CommonResult for /BaseUser/userInfo/add. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CorsAddAccountData(
        List<CorsCreatedAccount> accounts) {

    @JsonCreator
    public CorsAddAccountData {
        accounts = accounts == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(accounts));
    }

    public boolean hasValidAccounts(int expectedSize) {
        if (accounts == null || accounts.size() != expectedSize) {
            return false;
        }
        Set<Long> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        return accounts.stream().allMatch(account -> account != null
                && account.id() != null && account.id() > 0 && ids.add(account.id())
                && account.name() != null && !account.name().isBlank()
                && account.name().length() <= 128
                && account.name().codePoints().noneMatch(Character::isISOControl)
                && names.add(account.name()));
    }
}
