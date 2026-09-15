package com.sinognss.cloud.vantix.infrastructure.cors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchCreateRequest;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchResult;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestCorsAccountGatewayTest {
    private static final String BASE_URL = "http://cors.test";
    private static final String CREATE_URL = BASE_URL + "/internal/v1/accounts/batch-create";
    private static final String REQUEST_ID = "EX-20260913-000001";

    @Test
    void postsOnlyCorsBatchFieldsAndPreservesUnknownStringStatuses() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(CREATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "requestId":"EX-20260913-000001",
                          "durationDays":1,
                          "silenceDays":0,
                          "quantity":2,
                          "accountPrefix":"sino"
                        }
                        """))
                .andRespond(withSuccess(successBody(REQUEST_ID, "FUTURE_ACCOUNT_STATE", "WAITING_ACTIVATION", 2),
                        MediaType.APPLICATION_JSON));

        CorsBatchResult result = fixture.gateway.createBatch(
                new CorsBatchCreateRequest(REQUEST_ID, 1, 0, 2, "sino"));

        assertEquals(CorsOutcome.SUCCESS, result.outcome());
        assertEquals(2, result.accounts().size());
        assertEquals(1, result.accounts().get(0).index());
        assertEquals("FUTURE_ACCOUNT_STATE", result.accounts().get(0).accountStatus());
        assertEquals("WAITING_ACTIVATION", result.accounts().get(0).activationStatus());
        assertNull(result.accounts().get(0).expireAt());
        fixture.server.verify();
    }

    @Test
    void mapsQueryHttp404ToNotFound() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(CREATE_URL + "/" + REQUEST_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        CorsBatchResult result = fixture.gateway.queryBatch(REQUEST_ID);

        assertEquals(CorsOutcome.NOT_FOUND, result.outcome());
        assertEquals(REQUEST_ID, result.requestId());
        fixture.server.verify();
    }

    @Test
    void onlyRecognizedRejectCodeWithExplicitFalseSideEffectIsDefinitive() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(CREATE_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"INVALID_ARGUMENT","message":"invalid duration","sideEffect":false}
                                """));

        CorsBatchResult result = fixture.gateway.createBatch(
                new CorsBatchCreateRequest(REQUEST_ID, 1, 0, 1, null));

        assertEquals(CorsOutcome.DEFINITIVE_REJECT, result.outcome());
        assertEquals("INVALID_ARGUMENT", result.errorCode());
        fixture.server.verify();
    }

    @Test
    void ordinaryFourHundredWithoutNoSideEffectGuaranteeIsUnknown() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(CREATE_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"INVALID_ARGUMENT","sideEffect":true}
                                """));

        CorsBatchResult result = fixture.gateway.createBatch(
                new CorsBatchCreateRequest(REQUEST_ID, 1, 0, 1, null));

        assertEquals(CorsOutcome.UNKNOWN, result.outcome());
        fixture.server.verify();
    }

    @Test
    void mapsIdempotencyConflictExplicitly() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(CREATE_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"IDEMPOTENCY_CONFLICT","message":"requestId reused"}
                                """));

        CorsBatchResult result = fixture.gateway.createBatch(
                new CorsBatchCreateRequest(REQUEST_ID, 1, 0, 1, null));

        assertEquals(CorsOutcome.IDEMPOTENCY_CONFLICT, result.outcome());
        fixture.server.verify();
    }

    @Test
    void mapsServerErrorAndMalformedSuccessToUnknown() {
        Fixture serverErrorFixture = fixture();
        serverErrorFixture.server.expect(requestTo(CREATE_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

        CorsBatchResult serverError = serverErrorFixture.gateway.createBatch(
                new CorsBatchCreateRequest(REQUEST_ID, 1, 0, 1, null));

        assertEquals(CorsOutcome.UNKNOWN, serverError.outcome());
        serverErrorFixture.server.verify();

        Fixture malformedFixture = fixture();
        malformedFixture.server.expect(requestTo(CREATE_URL))
                .andRespond(withSuccess("""
                        {"code":0,"message":"ok","data":{"requestId":"EX-WRONG","accounts":[]}}
                        """, MediaType.APPLICATION_JSON));

        CorsBatchResult malformedSuccess = malformedFixture.gateway.createBatch(
                new CorsBatchCreateRequest(REQUEST_ID, 1, 0, 1, null));

        assertEquals(CorsOutcome.UNKNOWN, malformedSuccess.outcome());
        assertEquals("MALFORMED_SUCCESS", malformedSuccess.errorCode());
        malformedFixture.server.verify();
    }

    @Test
    void mapsReadTimeoutToUnknown() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/accounts/batch-create", exchange -> {
            exchange.getRequestBody().readAllBytes();
            try {
                TimeUnit.MILLISECONDS.sleep(400);
                byte[] body = successBody(REQUEST_ID, "ENABLED", "WAITING_ACTIVATION", 1)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The test client times out and closes the connection before this delayed response is written.
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(1));
            factory.setReadTimeout(Duration.ofMillis(50));
            RestClient client = RestClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .requestFactory(factory)
                    .build();
            RestCorsAccountGateway gateway = new RestCorsAccountGateway(client, objectMapper());

            CorsBatchResult result = gateway.createBatch(
                    new CorsBatchCreateRequest(REQUEST_ID, 1, 0, 1, null));

            assertEquals(CorsOutcome.UNKNOWN, result.outcome());
            assertEquals("TRANSPORT_ERROR", result.errorCode());
        } finally {
            server.stop(0);
        }
    }

    private static Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new RestCorsAccountGateway(builder.build(), objectMapper()), server);
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private static String successBody(String requestId, String accountStatus,
                                      String activationStatus, int count) {
        StringBuilder accounts = new StringBuilder();
        for (int index = 1; index <= count; index++) {
            if (index > 1) accounts.append(',');
            accounts.append("""
                    {"index":%d,"accountId":"cors-%d","account":"sino-%d",
                     "accountStatus":"%s","activationStatus":"%s",
                     "activatedAt":null,"expireAt":null,
                     "createdAt":"2026-09-13T16:30:00+08:00",
                     "updatedAt":"2026-09-13T16:30:00+08:00"}
                    """.formatted(index, index, index, accountStatus, activationStatus).trim());
        }
        return """
                {"code":0,"message":"success","data":{"requestId":"%s","accounts":[%s]}}
                """.formatted(requestId, accounts);
    }

    private record Fixture(RestCorsAccountGateway gateway, MockRestServiceServer server) {
    }
}
