package com.sinognss.cloud.vantix.infrastructure.cors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinognss.cloud.vantix.integration.cors.CorsAccountGateway;
import com.sinognss.cloud.vantix.integration.cors.CorsAddAccountData;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchCreateRequest;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchResult;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalRequest;
import com.sinognss.cloud.vantix.integration.cors.account.CorsAccountRenewalResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsCustomPasswordRequest;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordResult;
import com.sinognss.cloud.vantix.integration.cors.account.CorsPasswordGateway;
import com.sinognss.cloud.vantix.integration.cors.account.CorsRenewalData;
import com.sinognss.cloud.vantix.integration.cors.account.CorsResetPasswordRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Set;

@Component
public class RestCorsAccountGateway implements CorsAccountGateway, CorsAccountRenewalGateway,
        CorsPasswordGateway {
    private static final String CREATE_PATH = "/BaseUser/userInfo/add";
    private static final String RENEWAL_PATH = "/BaseUser/userInfo/batch/renewal";
    private static final String RESET_PASSWORD_PATH = "/BaseUser/userInfo/resetPass";
    private static final String CUSTOM_PASSWORD_PATH = "/BaseUser/userInfo/customPass";
    private static final String SUCCESS_CODE = "0";
    private static final String IDEMPOTENCY_CONFLICT_CODE = "IDEMPOTENCY_CONFLICT";
    private static final Set<String> DEFINITIVE_REJECT_CODES = Set.of("5301", "5302");
    private static final Set<String> RENEWAL_DEFINITIVE_CODES = Set.of("5314", "5345", "5316");

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
                            handleResponse(response, request.requestId()));
        } catch (Exception exception) {
            return unknown(request.requestId(), "TRANSPORT_ERROR", "CORS create call outcome is unknown");
        }
    }

    @Override
    public CorsAccountRenewalResult renew(CorsAccountRenewalRequest request) {
        if (request == null) {
            return CorsAccountRenewalResult.unknown(null, "INVALID_REQUEST", "CORS renewal request is missing");
        }
        try {
            return restClient.post()
                    .uri(RENEWAL_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((httpRequest, response) -> handleRenewalResponse(response, request.requestId()));
        } catch (Exception exception) {
            return CorsAccountRenewalResult.unknown(request.requestId(), "TRANSPORT_ERROR",
                    "CORS renewal call outcome is unknown");
        }
    }

    @Override
    public CorsPasswordResult resetPassword(CorsResetPasswordRequest request) {
        return postPassword(RESET_PASSWORD_PATH, request);
    }

    @Override
    public CorsPasswordResult customPassword(CorsCustomPasswordRequest request) {
        return postPassword(CUSTOM_PASSWORD_PATH, request);
    }

    private CorsPasswordResult postPassword(String path, Object request) {
        if (request == null) {
            return CorsPasswordResult.unknown("INVALID_REQUEST", "CORS password request is missing");
        }
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((httpRequest, response) -> handlePasswordResponse(response));
        } catch (Exception exception) {
            return CorsPasswordResult.unknown("TRANSPORT_ERROR", "CORS password call outcome is unknown");
        }
    }

    private CorsBatchResult handleResponse(ClientHttpResponse response, String expectedRequestId)
            throws IOException {
        int statusCode = response.getStatusCode().value();
        if (response.getStatusCode().is5xxServerError()) {
            return unknown(expectedRequestId, "HTTP_" + statusCode,
                    "CORS server error; outcome is unknown");
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
        String message = safeMessage(text(envelope.get("message")),
                "CORS returned an unclassified response");
        if (IDEMPOTENCY_CONFLICT_CODE.equals(code)) {
            return CorsBatchResult.outcome(CorsOutcome.IDEMPOTENCY_CONFLICT, expectedRequestId,
                    code, message);
        }
        if (DEFINITIVE_REJECT_CODES.contains(code)) {
            return CorsBatchResult.outcome(CorsOutcome.DEFINITIVE_REJECT, expectedRequestId,
                    code, message);
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            return unknown(expectedRequestId, code == null ? "HTTP_" + statusCode : code, message);
        }
        if (!SUCCESS_CODE.equals(code)) {
            return unknown(expectedRequestId, code == null ? "MISSING_CODE" : code, message);
        }

        JsonNode dataNode = envelope.get("data");
        if (dataNode == null || dataNode.isNull()) {
            // code=0 is success even when Redis has not returned the details yet.
            return CorsBatchResult.success(expectedRequestId, null);
        }
        if (!dataNode.isObject()) {
            return unknown(expectedRequestId, "MALFORMED_SUCCESS", "CORS success data is malformed");
        }
        try {
            CorsAddAccountData data = objectMapper.treeToValue(dataNode, CorsAddAccountData.class);
            return CorsBatchResult.success(expectedRequestId, data);
        } catch (Exception exception) {
            return unknown(expectedRequestId, "MALFORMED_SUCCESS", "CORS success data could not be parsed");
        }
    }

    private CorsAccountRenewalResult handleRenewalResponse(ClientHttpResponse response, String expectedRequestId)
            throws IOException {
        int statusCode = response.getStatusCode().value();
        if (response.getStatusCode().is5xxServerError()) {
            return CorsAccountRenewalResult.unknown(expectedRequestId, "HTTP_" + statusCode,
                    "CORS server error; outcome is unknown");
        }

        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(response.getBody().readAllBytes());
        } catch (Exception exception) {
            return CorsAccountRenewalResult.unknown(expectedRequestId, "MALFORMED_RESPONSE",
                    "CORS renewal response could not be parsed");
        }
        if (envelope == null || !envelope.isObject()) {
            return CorsAccountRenewalResult.unknown(expectedRequestId, "MALFORMED_RESPONSE",
                    "CORS renewal response envelope is malformed");
        }

        String code = text(envelope.get("code"));
        String message = safeMessage(text(envelope.get("message")),
                "CORS returned an unclassified renewal response");
        if (RENEWAL_DEFINITIVE_CODES.contains(code)) {
            return CorsAccountRenewalResult.definitiveReject(expectedRequestId,
                    renewalFailureCode(code), message);
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            return CorsAccountRenewalResult.unknown(expectedRequestId,
                    code == null ? "HTTP_" + statusCode : code, message);
        }
        if (!SUCCESS_CODE.equals(code)) {
            return CorsAccountRenewalResult.unknown(expectedRequestId,
                    code == null ? "MISSING_CODE" : code, message);
        }

        JsonNode dataNode = envelope.get("data");
        if (dataNode == null || dataNode.isNull()) {
            return CorsAccountRenewalResult.successWithData(expectedRequestId, null);
        }
        if (!dataNode.isObject()) {
            return CorsAccountRenewalResult.unknown(expectedRequestId, "MALFORMED_SUCCESS",
                    "CORS renewal success data is malformed");
        }
        try {
            CorsRenewalData data = objectMapper.treeToValue(dataNode, CorsRenewalData.class);
            return CorsAccountRenewalResult.successWithData(expectedRequestId, data);
        } catch (Exception exception) {
            return CorsAccountRenewalResult.unknown(expectedRequestId, "MALFORMED_SUCCESS",
                    "CORS renewal success data could not be parsed");
        }
    }

    private CorsPasswordResult handlePasswordResponse(ClientHttpResponse response) throws IOException {
        int statusCode = response.getStatusCode().value();
        if (response.getStatusCode().is5xxServerError()) {
            return CorsPasswordResult.unknown("HTTP_" + statusCode,
                    "CORS server error; outcome is unknown");
        }

        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(response.getBody().readAllBytes());
        } catch (Exception exception) {
            return CorsPasswordResult.unknown("MALFORMED_RESPONSE",
                    "CORS password response could not be parsed");
        }
        if (envelope == null || !envelope.isObject()) {
            return CorsPasswordResult.unknown("MALFORMED_RESPONSE",
                    "CORS password response envelope is malformed");
        }

        String code = text(envelope.get("code"));
        String message = safeMessage(text(envelope.get("message")),
                "CORS returned an unclassified password response");
        if (SUCCESS_CODE.equals(code) && response.getStatusCode().is2xxSuccessful()) {
            // The data member is deliberately not deserialized or retained.
            return CorsPasswordResult.success();
        }
        if (code != null && !SUCCESS_CODE.equals(code)) {
            return CorsPasswordResult.businessFailure(code, message);
        }
        return CorsPasswordResult.unknown(code == null ? "MISSING_CODE" : code, message);
    }

    private static String renewalFailureCode(String corsCode) {
        return switch (corsCode) {
            case "5314" -> "CORS_RENEWAL_INVALID_ARGUMENT";
            case "5345" -> "CORS_RENEWAL_ACCOUNT_NOT_ACTIVE";
            case "5316" -> "CORS_RENEWAL_FAILED";
            default -> "CORS_RENEWAL_FAILED";
        };
    }

    private static String text(JsonNode node) {
        return node != null && node.isValueNode() && !node.isNull() ? node.asText() : null;
    }

    private static String safeMessage(String message, String fallback) {
        if (message == null || message.isBlank()) return fallback;
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private static CorsBatchResult unknown(String requestId, String code, String message) {
        return CorsBatchResult.outcome(CorsOutcome.UNKNOWN, requestId, code, message);
    }
}
