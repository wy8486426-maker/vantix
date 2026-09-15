package com.sinognss.cloud.vantix.application.offline;

import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeItemCommand;
import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeOrderCommand;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeGenerateOrderItemView;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeGenerateOrderView;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeOrderGenerateService;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.config.OfflineImportProperties;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateOrder;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateOrderMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfflineOrderImportServiceTest {
    private final OfflineOrderExcelParser parser = new OfflineOrderExcelParser();
    private final OfflineOrderTemplateService templateService = mock(OfflineOrderTemplateService.class);
    private final OfflineOrderImportTransaction transaction = mock(OfflineOrderImportTransaction.class);
    private final ServiceDurationConfigMapper durationMapper = mock(ServiceDurationConfigMapper.class);
    private final ServiceCodeGenerateOrderMapper orderMapper = mock(ServiceCodeGenerateOrderMapper.class);
    private final DealerCompanyMapper companyMapper = mock(DealerCompanyMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private final OfflineImportProperties importProperties = new OfflineImportProperties();
    private final GenerationProperties generationProperties = new GenerationProperties();
    private OfflineOrderImportService service;

    @BeforeEach
    void setUp() {
        when(durationMapper.selectEnabled()).thenReturn(List.of(spec("M1", 30, "1个月"),
                spec("Y1", 365, "1年")));
        when(companyMapper.selectCount(any())).thenReturn(1L);
        when(orderMapper.selectByBusinessKey(anyString(), anyLong(), anyString())).thenReturn(null);
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        when(userHolder.getOperator()).thenReturn(new OperatorIdentity(7L, "operator"));
        when(transaction.generateAll(anyLong(), any(), any())).thenAnswer(invocation ->
                ((List<ParsedOfflineOrderGroup>) invocation.getArgument(1)).stream()
                        .map(this::viewFor).toList());
        service = new OfflineOrderImportService(parser, templateService, transaction, durationMapper,
                orderMapper, companyMapper, userHolder, importProperties, generationProperties);
    }

    @Test
    void validatesWholeFileThenPassesAllRowsToOneTransaction() throws Exception {
        var result = service.importFile(100L, upload(List.of(
                List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                List.of("ORDER-1", "1个月", "2", "", "note"))));

        assertEquals(1, result.batchCount());
        assertEquals(2, result.generatedCount());
        verify(transaction).generateAll(anyLong(), any(), any());
    }

    @Test
    void groupsDifferentSpecsOfTheSameOrderIntoOneImportRequest() throws Exception {
        var result = service.importFile(100L, upload(List.of(
                List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                List.of("ORDER-MULTI", "1个月", "6", "2026-09-12T18:09:22", "month"),
                List.of("ORDER-MULTI", "1年", "2", "", "year"))));

        assertEquals(2, result.batchCount());
        assertEquals(8, result.generatedCount());
        ArgumentCaptor<List<ParsedOfflineOrderGroup>> captor = ArgumentCaptor.forClass(List.class);
        verify(transaction).generateAll(anyLong(), captor.capture(), any());
        assertEquals(1, captor.getValue().size());
        assertEquals(2, captor.getValue().get(0).items().size());
        assertEquals("ORDER-MULTI", captor.getValue().get(0).orderNo());
    }

    @Test
    void anyInvalidRowPreventsTheGenerationTransaction() throws Exception {
        assertThrows(OfflineImportValidationException.class, () -> service.importFile(100L, upload(List.of(
                List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                List.of("ORDER-1", "1个月", "2", "", ""),
                List.of("ORDER-2", "18个月", "1.5", "", "")))));

        verify(transaction, never()).generateAll(anyLong(), any(), any());
    }

    @Test
    void rejectsInternalDuplicateAndPreviouslyImportedOrders() throws Exception {
        assertThrows(OfflineImportValidationException.class, () -> service.importFile(100L, upload(List.of(
                List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                List.of("ORDER-1", "1个月", "1", "", ""),
                List.of("ORDER-1", "1个月", "1", "", "")))));
        verify(transaction, never()).generateAll(anyLong(), any(), any());

        ServiceCodeGenerateOrder existing = new ServiceCodeGenerateOrder();
        var existingCommand = new GenerateServiceCodeOrderCommand(GenerationSource.OFFLINE,
                ServiceCodeOrderGenerateService.offlineRequestId(100L, "ORDER-3"), "ORDER-3", null, 100L,
                List.of(new GenerateServiceCodeItemCommand("M1", 1, "")));
        existing.setPayloadHash(ServiceCodeOrderGenerateService.payloadHash(existingCommand));
        when(orderMapper.selectByBusinessKey(GenerationSource.OFFLINE.name(), 100L, "ORDER-3"))
                .thenReturn(existing);
        assertThrows(OfflineImportValidationException.class, () -> service.importFile(100L, upload(List.of(
                List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                List.of("ORDER-3", "1个月", "1", "", "")))));
        verify(transaction, never()).generateAll(anyLong(), any(), any());
    }

    @Test
    void rejectsInconsistentOrderTimesBeforeStartingTransaction() throws Exception {
        assertThrows(OfflineImportValidationException.class, () -> service.importFile(100L, upload(List.of(
                List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                List.of("ORDER-TIME", "1个月", "1", "2026-09-12T10:00:00", ""),
                List.of("ORDER-TIME", "1年", "1", "2026-09-12T11:00:00", "")))));
        verify(transaction, never()).generateAll(anyLong(), any(), any());
    }

    @Test
    void companyScopedUserCannotImportForAnotherCompany() throws Exception {
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, 100L));

        assertThrows(BusinessException.class, () -> service.importFile(200L, upload(List.of(
                List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                List.of("ORDER-1", "1个月", "1", "", "")))));
        verify(transaction, never()).generateAll(anyLong(), any(), any());
    }

    private ServiceCodeGenerateOrderView viewFor(ParsedOfflineOrderGroup group) {
        List<ServiceCodeGenerateOrderItemView> items = group.items().stream()
                .map(row -> new ServiceCodeGenerateOrderItemView(row.specCode(), row.displayName(),
                        row.quantity(), row.quantity(), "GB-" + row.specCode(), "COMPLETED"))
                .toList();
        int total = group.items().stream().mapToInt(ParsedOfflineOrder::quantity).sum();
        return new ServiceCodeGenerateOrderView("OFFLINE:100:" + group.orderNo(), "OFFLINE",
                group.orderNo(), 100L, group.orderTime(), "COMPLETED", items.size(), total, items, false, null);
    }

    private ServiceDurationConfig spec(String specCode, int durationDays, String displayName) {
        ServiceDurationConfig config = new ServiceDurationConfig();
        config.setSpecCode(specCode);
        config.setDisplayName(displayName);
        config.setServiceType("CORS");
        config.setDurationDays(durationDays);
        config.setCodeSilenceDays(180);
        config.setAccountSilenceDays(360);
        config.setEnabled(true);
        return config;
    }

    private MockMultipartFile upload(List<List<String>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("线下订单导入");
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                var row = sheet.createRow(rowIndex);
                for (int column = 0; column < rows.get(rowIndex).size(); column++) {
                    row.createCell(column).setCellValue(rows.get(rowIndex).get(column));
                }
            }
            workbook.write(output);
            return new MockMultipartFile("file", "orders.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
