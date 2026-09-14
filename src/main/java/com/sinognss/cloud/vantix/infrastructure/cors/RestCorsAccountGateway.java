package com.sinognss.cloud.vantix.infrastructure.cors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinognss.cloud.vantix.integration.cors.CorsAccountGateway;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchCreateRequest;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchResult;
import com.sinognss.cloud.vantix.integration.cors.CorsCreatedAccount;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class RestCorsAccountGateway implements CorsAccountGateway {
    private static final String CREATE_PATH = "/internal/v1/accounts/batch-create";
    private static final String SUCCESS_CODE = "0";
    private static final String NOT_FOUND_CODE = "NOT_FOUND";
    private static final String IDEMPOTENCY_CONFLICT_CODE = "IDEMPOTENCY_CONFLICT";
    private static final Set<String> DEFINITIVE_REJECT_CODES = Set.of("INVALID_ARGUMENT");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RestCorsAccountGateway(RestClient corsRestClient, ObjectMapper objectMapper) {
        this.restClient = corsRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public CorsBatchResult createBatch(CorsBatchCreateRequest request) {
        if (request == null) {
            return unknown(null, "INVALID_REQUEST", "CORS create request is missing");
        }
        try {
            return restClient.post()
                    .uri(CREATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((httpRequest, response) ->
                            handleResponse(response, request.requestId(), request.quantity(), false));
        } catch (Exception exception) {
            return unknown(request.requestId(), "TRANSPORT_ERROR", "CORS create call outcome is unknown");
        }
    }

    @Override
    public CorsBatchResult queryBatch(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return unknown(requestId, "INVALID_REQUEST", "CORS requestId is missing");
        }
        try {
            return restClient.get()
                    .uri(CREATE_PATH + "/{requestId}", requestId)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((httpRequest, response) -> handleResponse(response, requestId, null, true));
        } catch (Exception exception) {
            return unknown(requestId, "TRANSPORT_ERROR", "CORS query outcome is unknown");
        }
    }

    private CorsBatchResult handleResponse(ClientHttpResponse response, String expectedRequestId,
                                           Integer expectedQuantity, boolean query) throws IOException {
        int statusCode = response.getStatusCode().value();
        if (query && statusCode == 404) {
            return CorsBatchResult.outcome(CorsOutcome.NOT_FOUND, expectedRequestId, NOT_FOUND_CODE,
                    "CORS batch request was not found");
        }
        if (response.getStatusCode().is5xxServerError()) {
            return unknown(expectedRequestId, "HTTP_" + statusCode, "CORS server error; outcome is unknown");
        }

        JsonNode envelope;
        try {
            byte[] body = response.getBody().readAllBytes();
            envelope = objectMapper.readTree(body);
        } catch (Exception exception) {
            return unknown(expectedRequestId, "MALFORMED_RESPONSE", "CORS response could not be parsed");
        }
        if (envelope == null || !envelope.isObject()) {
            return unknown(expectedRequestId, "MALFORMED_RESPONSE", "CORS response envelope is malformed");
        }

        String code = text(envelope.get("code"));
        String message = text(envelope.get("message"));
        if (query && NOT_FOUND_CODE.equals(code)) {
            return CorsBatchResult.outcome(CorsOutcome.NOT_FOUND, expectedRequestId, code,
                    safeMessage(message, "CORS batch request was not found"));
        }
        if (IDEMPOTENCY_CONFLICT_CODE.equals(code)) {
            return CorsBatchResult.outcome(CorsOutcome.IDEMPOTENCY_CONFLICT, expectedRequestId, code,
                    safeMessage(message, "CORS idempotency conflict"));
        }
        if (DEFINITIVE_REJECT_CODES.contains(code) && isExplicitFalse(envelope.get("sideEffect"))) {
            return CorsBatchResult.outcome(CorsOutcome.DEFINITIVE_REJECT, expectedRequestId, code,
                    safeMessage(message, "CORS definitively rejected the request"));
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            return unknown(expectedRequestId, code == null ? "HTTP_" + statusCode : code,
                    safeMessage(message, "CORS returned an unclassified error"));
        }
        if (!SUCCESS_CODE.equals(code)) {
            return unknown(expectedRequestId, code, safeMessage(message, "CORS returned an unclassified response"));
        }

        CorsBatchResult success = parseSuccess(envelope.get("data"), expectedRequestId, expectedQuantity);
        return success == null
                ? unknown(expectedRequestId, "MALFORMED_SUCCESS", "CORS success response failed validation")
                : success;
    }

    private CorsBatchResult parseSuccess(JsonNode data, String expectedRequestId, Integer expectedQuantity) {
        if (data == null || !data.isObject() || !isText(data.get("requestId"))
                || !expectedRequestId.equals(text(data.get("requestId")))) {
            return null;
        }
        JsonNode accountNodes = data.get("accounts");
        if (accountNodes == null || !accountNodes.isArray() || accountNodes.isEmpty()) {
            return null;
        }
        if (expectedQuantity != null && accountNodes.size() != expectedQuantity) {
            return null;
        }

        List<CorsCreatedAccount> accounts = new ArrayList<>(accountNodes.size());
        Set<Integer> indices = new HashSet<>();
        Set<String> accountIds = new HashSet<>();
        Set<String> accountNames = new HashSet<>();
        for (JsonNode accountNode : accountNodes) {
            if (accountNode == null || !accountNode.isObject()
                    || !isIntegral(accountNode.get("index"))
                    || !isText(accountNode.get("accountId"))
                    || !isText(accountNode.get("account"))
                    || !isText(accountNode.get("accountStatus"))
                    || !isText(accountNode.get("activationStatus"))
                    || !isOptionalTextOrNull(accountNode.get("activatedAt"))
                    || !isOptionalTextOrNull(accountNode.get("expireAt"))
                    || !isText(accountNode.get("createdAt"))
                    || !isText(accountNode.get("updatedAt"))) return null;
            try {
                CorsCreatedAccount account = objectMapper.treeToValue(accountNode, CorsCreatedAccount.class);
                if (account.index() < 1 || account.index() > accountNodes.size()
                        || !indices.add(account.index())
                        || isBlank(account.accountId()) || !accountIds.add(account.accountId())
                        || isBlank(account.account()) || !accountNames.add(account.account())
                        || isBlank(account.accountStatus()) || isBlank(account.activationStatus())
                        || account.createdAt() == null || account.updatedAt() == null) {
                    return null;
                }
                accounts.add(account);
            } catch (Exception exception) {
                return null;
            }
        }
        if (indices.size() != accountNodes.size()) return null;
        accounts.sort(java.util.Comparator.comparingInt(CorsCreatedAccount::index));
        return new CorsBatchResult(CorsOutcome.SUCCESS, expectedRequestId, accounts, null, null);
    }

    private static boolean isText(JsonNode node) {
        return node != null && node.isTextual();
    }

    private static boolean isIntegral(JsonNode node) {
        return node != null && node.isIntegralNumber() && node.canConvertToInt();
    }

    private static boolean isOptionalTextOrNull(JsonNode node) {
        return node != null && (node.isNull() || node.isTextual());
    }

    private static boolean isExplicitFalse(JsonNode node) {
        return node != null && node.isBoolean() && !node.booleanValue();
    }

    private static String text(JsonNode node) {
        return node != null && node.isValueNode() && !node.isNull() ? node.asText() : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safeMessage(String message, String fallback) {
        if (message == null || message.isBlank()) return fallback;
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private static CorsBatchResult unknown(String requestId, String code, String message) {
        return CorsBatchResult.outcome(CorsOutcome.UNKNOWN, requestId, code, message);
    }
}
