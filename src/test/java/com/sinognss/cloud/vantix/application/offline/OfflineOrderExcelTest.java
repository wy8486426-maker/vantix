package com.sinognss.cloud.vantix.application.offline;

import com.sinognss.cloud.vantix.config.OfflineImportProperties;
import com.sinognss.cloud.vantix.domain.config.DurationUnit;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfflineOrderExcelTest {
    private final OfflineOrderExcelParser parser = new OfflineOrderExcelParser();

    @Test
    void templateHasExactHeadersEnabledDropdownAndNoCompanyOrCommercialFields() throws Exception {
        ServiceDurationConfig month = spec("M1", 1, DurationUnit.MONTH);
        ServiceDurationConfig year = spec("Y1", 1, DurationUnit.YEAR);
        ServiceDurationConfigMapper mapper = mock(ServiceDurationConfigMapper.class);
        when(mapper.selectEnabled()).thenReturn(List.of(month, year));

        byte[] bytes = new OfflineOrderTemplateService(mapper, new OfflineImportProperties()).createTemplate();
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertEquals(3, workbook.getNumberOfSheets());
            assertEquals(OfflineOrderExcelParser.MAIN_SHEET, workbook.getSheetAt(0).getSheetName());
            assertEquals(List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                    OfflineOrderExcelParser.HEADERS);
            var header = workbook.getSheetAt(0).getRow(0);
            assertEquals(5, header.getLastCellNum());
            for (int column = 0; column < 5; column++) {
                assertFalse(header.getCell(column).getStringCellValue().contains("公司"));
                assertFalse(header.getCell(column).getStringCellValue().contains("金额"));
                assertFalse(header.getCell(column).getStringCellValue().contains("商品"));
                assertFalse(header.getCell(column).getStringCellValue().contains("部件"));
            }
            assertEquals("1个月", workbook.getSheetAt(1).getRow(1).getCell(0).getStringCellValue());
            assertEquals("1年", workbook.getSheetAt(1).getRow(2).getCell(0).getStringCellValue());
            assertEquals("M1", workbook.getSheetAt(1).getRow(1).getCell(1).getStringCellValue());
            assertTrue(workbook.getSheetAt(1).isColumnHidden(1));
            List<? extends DataValidation> validations = workbook.getSheetAt(0).getDataValidations();
            assertEquals(2, validations.size());
            assertTrue(validations.stream().anyMatch(validation ->
                    validation.getValidationConstraint().getFormula1().equals("ServiceDurationDisplayNames")));
            assertTrue(workbook.getSheetAt(2).getRow(0).getCell(0).getStringCellValue().contains("公司"));
        }
    }

    @Test
    void parsesValidRowsByUniqueEnabledDisplayName() throws Exception {
        OfflineParseResult result = parseRows(List.of(
                List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                List.of("DD001", "1个月", "2", "2026-09-12T18:09:22", "note")),
                List.of(spec("M1", 1, DurationUnit.MONTH)));

        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.rows().size());
        assertEquals("M1", result.rows().get(0).specCode());
        assertEquals(2, result.rows().get(0).quantity());
        assertEquals("note", result.rows().get(0).remark());
    }

    @Test
    void rejectsUnavailableDurationNonIntegerQuantityAndAdditionalCompanyColumn() throws Exception {
        OfflineParseResult invalid = parseRows(List.of(
                List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                List.of("DD001", "18个月", "1.5", "", "")),
                List.of(spec("M1", 1, DurationUnit.MONTH)));
        assertEquals(2, invalid.errors().size());
        assertTrue(invalid.rows().isEmpty());

        OfflineParseResult changedHeader = parseRows(List.of(
                List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注", "公司ID")),
                List.of(spec("M1", 1, DurationUnit.MONTH)));
        assertEquals(1, changedHeader.errors().size());
        assertEquals("表头", changedHeader.errors().get(0).field());
    }

    @Test
    void rejectsDuplicateDisplayNameThatCannotResolveToOneEnabledSpec() throws Exception {
        OfflineParseResult result = parseRows(List.of(
                        List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注"),
                        List.of("DD001", "1个月", "1", "", "")),
                List.of(spec("M1", 1, DurationUnit.MONTH), spec("M1-CORS2", 1, DurationUnit.MONTH)));

        assertTrue(result.rows().isEmpty());
        assertTrue(result.errors().get(0).message().contains("无法唯一匹配"));
    }

    private OfflineParseResult parseRows(List<List<String>> rows, List<ServiceDurationConfig> specs) throws Exception {
        return parser.parse(new ByteArrayInputStream(workbookBytes(rows)), specs, 500, 100);
    }

    private ServiceDurationConfig spec(String specCode, int value, DurationUnit unit) {
        ServiceDurationConfig spec = new ServiceDurationConfig();
        spec.setSpecCode(specCode);
        spec.setServiceType("CORS");
        spec.setDurationValue(value);
        spec.setDurationUnit(unit);
        spec.setCodeSilenceMonths(12);
        spec.setEnabled(true);
        return spec;
    }

    private byte[] workbookBytes(List<List<String>> rows) throws Exception {
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(OfflineOrderExcelParser.MAIN_SHEET);
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                var row = sheet.createRow(rowIndex);
                for (int column = 0; column < rows.get(rowIndex).size(); column++) {
                    row.createCell(column).setCellValue(rows.get(rowIndex).get(column));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }
}