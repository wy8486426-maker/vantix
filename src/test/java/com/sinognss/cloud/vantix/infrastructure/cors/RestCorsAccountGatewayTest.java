package com.sinognss.cloud.vantix.infrastructure.cors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchCreateRequest;
import com.sinognss.cloud.vantix.integration.cors.CorsBatchResult;
import com.sinognss.cloud.vantix.integration.cors.CorsOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestCorsAccountGatewayTest {
    private static final String BASE_URL = "http://cors.test";
    private static final String CREATE_URL = BASE_URL + "/userInfo/add";
    private static final String REQUEST_ID = "EXCHANGE-cors-1";

    @Test
    void postsTheConfirmedUserInfoAddContract() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(CREATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "requestId":"EXCHANGE-cors-1",
                          "addNum":2,
                          "accountType":0,
                          "durationType":365,
                          "accountName":"AB12",
                          "nameType":0,
                          "silenceType":30,
                          "activeType":1,
                          "remark":"",
                          "dealerId":123,
                          "normalType":0
                        }
                        """))
                .andRespond(withSuccess("""
                        {"code":0,"message":"操作成功","data":{
                          "interface_name":"corsAdd",
                          "corsNameList":["AB12000001","AB12000002"]
                        }}
                        """, MediaType.APPLICATION_JSON));

        CorsBatchResult result = fixture.gateway.createBatch(request());

        assertEquals(CorsOutcome.SUCCESS, result.outcome());
        assertEquals("corsAdd", result.data().interfaceName());
        assertEquals(java.util.List.of("AB12000001", "AB12000002"), result.data().corsNameList());
        fixture.server.verify();
    }

    @Test
    void codeZeroWithNullDataIsStillSuccess() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(CREATE_URL))
                .andRespond(withSuccess("{\"code\":0,\"message\":\"操作成功\",\"data\":null}",
                        MediaType.APPLICATION_JSON));

        CorsBatchResult result = fixture.gateway.createBatch(request());

        assertEquals(CorsOutcome.SUCCESS, result.outcome());
        assertNull(result.data());
        fixture.server.verify();
    }

    @Test
    void mapsUserNameDuplicateToDefinitiveBusinessFailure() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(CREATE_URL))
                .andRespond(withSuccess("{\"code\":5302,\"message\":\"用户名称重复\",\"data\":2}",
                        MediaType.APPLICATION_JSON));

        CorsBatchResult result = fixture.gateway.createBatch(request());

        assertEquals(CorsOutcome.DEFINITIVE_REJECT, result.outcome());
        assertEquals("5302", result.errorCode());
        fixture.server.verify();
    }

    @Test
    void mapsUserAddFailureToDefinitiveBusinessFailure() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(CREATE_URL))
                .andRespond(withSuccess("{\"code\":5301,\"message\":\"用户添加失败\",\"data\":3}",
                        MediaType.APPLICATION_JSON));

        CorsBatchResult result = fixture.gateway.createBatch(request());

        assertEquals(CorsOutcome.DEFINITIVE_REJECT, result.outcome());
        assertEquals("5301", result.errorCode());
        fixture.server.verify();
    }

    @Test
    void mapsTransportAndUnknownResponsesToUnknown() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(CREATE_URL))
                .andRespond(withSuccess("{\"code\":5999,\"message\":\"temporary\",\"data\":3}",
                        MediaType.APPLICATION_JSON));

        CorsBatchResult result = fixture.gateway.createBatch(request());

        assertEquals(CorsOutcome.UNKNOWN, result.outcome());
        assertEquals("5999", result.errorCode());
        fixture.server.verify();
    }

    private static CorsBatchCreateRequest request() {
        return new CorsBatchCreateRequest(REQUEST_ID, 2, 0, 365, "AB12", 0,
                30, 1, "", 123L, 0);
    }

    private static Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new RestCorsAccountGateway(builder.build(), new ObjectMapper()), server);
    }

    private record Fixture(RestCorsAccountGateway gateway, MockRestServiceServer server) {
    }
}
