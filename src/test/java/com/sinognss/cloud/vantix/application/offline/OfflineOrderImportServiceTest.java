package com.sinognss.cloud.vantix.application.offline;

import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeResult;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeBatchView;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.config.OfflineImportProperties;
import com.sinognss.cloud.vantix.domain.config.DurationUnit;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private final ServiceCodeGenerateBatchMapper batchMapper = mock(ServiceCodeGenerateBatchMapper.class);
    private final DealerCompanyMapper companyMapper = mock(DealerCompanyMapper.class);
    private final UserHolderBridge userHolder = mock(UserHolderBridge.class);
    private final OfflineImportProperties importProperties = new OfflineImportProperties();
    private final GenerationProperties generationProperties = new GenerationProperties();
    private OfflineOrderImportService service;

    @BeforeEach
    void setUp() {
        ServiceDurationConfig spec = new ServiceDurationConfig();
        spec.setSpecCode("M1");
        spec.setServiceType("CORS");
        spec.setDurationValue(1);
        spec.setDurationUnit(DurationUnit.MONTH);
        spec.setCodeSilenceMonths(6);
        spec.setEnabled(true);
        when(durationMapper.selectEnabled()).thenReturn(List.of(spec));
        when(companyMapper.selectCount(any())).thenReturn(1L);
        when(batchMapper.selectByBusinessKey(anyString(), anyLong(), anyString())).thenReturn(null);
        when(userHolder.getUserScope()).thenReturn(new UserScope(null, null));
        when(userHolder.getOperator()).thenReturn(new OperatorIdentity(7L, "operator"));
        when(transaction.generateAll(anyLong(), any(), any())).thenReturn(List.of(
                new GenerateServiceCodeResult(new ServiceCodeBatchView("GB-1", "OFFLINE", "ORDER-1",
                        100L, "M1", "1个月", 2, 2, "COMPLETED", null), false, List.of("CODE-1", "CODE-2"))));
        service = new OfflineOrderImportService(parser, templateService, transaction, durationMapper,
                batchMapper, companyMapper, userHolder, importProperties, generationProperties);
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
    void anyInvalidRowPreventsTheGenerationTransaction() throws Exception {
        assertThrows(OfflineImportValidationException.class, () -> service.importFile(100L, upload(List.of(
                List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                List.of("ORDER-1", "1个月", "2", "", ""),
                List.of("ORDER-2", "18个月", "1.5", "", "")))));

        verify(transaction, never()).generateAll(anyLong(), any(), any());
    }

    @Test
    void rejectsInternalDuplicateAndPreviouslyImportedBusinessKeys() throws Exception {
        assertThrows(OfflineImportValidationException.class, () -> service.importFile(100L, upload(List.of(
                List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                List.of("ORDER-1", "1个月", "1", "", ""),
                List.of("ORDER-1", "1个月", "1", "", "")))));
        verify(transaction, never()).generateAll(anyLong(), any(), any());

        com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateBatch existing =
                new com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateBatch();
        when(batchMapper.selectByBusinessKey(anyString(), anyLong(), anyString())).thenReturn(existing);
        assertThrows(OfflineImportValidationException.class, () -> service.importFile(100L, upload(List.of(
                List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                List.of("ORDER-3", "1个月", "1", "", "")))));
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