package com.sinognss.cloud.vantix.application.offline;

import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OfflineOrderExcelParser {
    public static final String MAIN_SHEET = "线下订单导入";
    public static final List<String> HEADERS = List.of("订单号*", "服务时长*", "服务码数量*", "下单时间", "备注");
    private static final DateTimeFormatter SPACE_DATE_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    public OfflineParseResult parse(InputStream input, List<ServiceDurationConfig> enabledSpecs,
                                    int maxRows, int maxErrors) {
        List<ParsedOfflineOrder> rows = new ArrayList<>();
        List<OfflineImportError> errors = new ArrayList<>();
        Map<String, List<ServiceDurationConfig>> specsByDisplayName = enabledSpecs.stream()
                .collect(Collectors.groupingBy(ServiceDurationConfig::getDisplayName));
        try (XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0 || !MAIN_SHEET.equals(workbook.getSheetAt(0).getSheetName())) {
                addError(errors, maxErrors, 0, "文件", "缺少“" + MAIN_SHEET + "”工作表");
                return new OfflineParseResult(rows, errors);
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > maxRows) {
                addError(errors, maxErrors, 0, "文件", "数据行超过上限 " + maxRows);
                return new OfflineParseResult(rows, errors);
            }
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Row header = sheet.getRow(0);
            if (!hasExpectedHeaders(header, formatter)) {
                addError(errors, maxErrors, 1, "表头", "模板表头必须与系统模板完全一致");
                return new OfflineParseResult(rows, errors);
            }

            int populatedRows = 0;
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (isBlank(row, formatter)) {
                    continue;
                }
                populatedRows++;
                int rowNumber = index + 1;
                if (populatedRows > maxRows) {
                    addError(errors, maxErrors, rowNumber, "文件", "数据行超过上限 " + maxRows);
                    break;
                }
                if (hasExtraColumns(row, formatter)) {
                    addError(errors, maxErrors, rowNumber, "表格", "模板只允许包含规定的五列");
                    continue;
                }

                String orderNo = cellText(row.getCell(0), formatter, errors, rowNumber, HEADERS.get(0), maxErrors);
                String displayName = cellText(row.getCell(1), formatter, errors, rowNumber, HEADERS.get(1), maxErrors);
                String quantityText = cellText(row.getCell(2), formatter, errors, rowNumber, HEADERS.get(2), maxErrors);
                String orderTimeText = cellText(row.getCell(3), formatter, errors, rowNumber, HEADERS.get(3), maxErrors);
                String remark = cellText(row.getCell(4), formatter, errors, rowNumber, HEADERS.get(4), maxErrors);

                orderNo = orderNo == null ? "" : orderNo.trim();
                displayName = displayName == null ? "" : displayName.trim();
                remark = remark == null ? "" : remark.trim();

                if (orderNo.isEmpty()) {
                    addError(errors, maxErrors, rowNumber, HEADERS.get(0), "订单号不能为空");
                } else if (orderNo.length() > 128 || orderNo.codePoints().anyMatch(Character::isISOControl)) {
                    addError(errors, maxErrors, rowNumber, HEADERS.get(0), "订单号长度不能超过 128 且不能包含控制字符");
                }

                ServiceDurationConfig spec = null;
                if (displayName.isEmpty()) {
                    addError(errors, maxErrors, rowNumber, HEADERS.get(1), "服务时长不能为空");
                } else {
                    List<ServiceDurationConfig> matches = specsByDisplayName.getOrDefault(displayName, List.of());
                    if (matches.isEmpty()) {
                        addError(errors, maxErrors, rowNumber, HEADERS.get(1),
                                "不存在可用的服务时长：" + displayName);
                    } else if (matches.size() > 1) {
                        addError(errors, maxErrors, rowNumber, HEADERS.get(1),
                                "服务时长无法唯一匹配：" + displayName);
                    } else {
                        spec = matches.get(0);
                    }
                }

                Integer quantity = parseQuantity(quantityText, errors, rowNumber, maxErrors);
                LocalDateTime orderTime = parseOrderTime(row.getCell(3), orderTimeText,
                        formatter, errors, rowNumber, maxErrors);
                if (remark.length() > 512) {
                    addError(errors, maxErrors, rowNumber, HEADERS.get(4), "备注长度不能超过 512");
                }

                if (orderNo.length() > 0 && orderNo.length() <= 128 && spec != null
                        && quantity != null && !hasRowError(errors, rowNumber)) {
                    rows.add(new ParsedOfflineOrder(rowNumber, orderNo, spec.getSpecCode(),
                            displayName, quantity, orderTime, remark.isEmpty() ? null : remark));
                }
            }
            if (populatedRows == 0) {
                addError(errors, maxErrors, 0, "文件", "文件中没有可导入的数据行");
            }
        } catch (Exception exception) {
            addError(errors, maxErrors, 0, "文件", "无法读取 .xlsx 文件，请使用系统模板重新填写");
        }
        return new OfflineParseResult(List.copyOf(rows), List.copyOf(errors));
    }

    private boolean hasExpectedHeaders(Row row, DataFormatter formatter) {
        if (row == null) {
            return false;
        }
        for (int column = 0; column < HEADERS.size(); column++) {
            if (!HEADERS.get(column).equals(cellText(row.getCell(column), formatter))) {
                return false;
            }
        }
        return !hasExtraColumns(row, formatter);
    }

    private boolean hasExtraColumns(Row row, DataFormatter formatter) {
        if (row == null) {
            return false;
        }
        for (int column = HEADERS.size(); column < Math.max(HEADERS.size(), row.getLastCellNum()); column++) {
            if (!cellText(row.getCell(column), formatter).isBlank()) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        for (int column = 0; column < Math.max(HEADERS.size(), row.getLastCellNum()); column++) {
            if (!cellText(row.getCell(column), formatter).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR) {
            return null;
        }
        return formatter.formatCellValue(cell);
    }

    private String cellText(Cell cell, DataFormatter formatter, List<OfflineImportError> errors,
                            int rowNumber, String field, int maxErrors) {
        if (cell != null && (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR)) {
            addError(errors, maxErrors, rowNumber, field, "不允许使用公式或错误值");
            return "";
        }
        return cellText(cell, formatter);
    }

    private Integer parseQuantity(String value, List<OfflineImportError> errors, int rowNumber, int maxErrors) {
        if (value == null || value.isBlank()) {
            addError(errors, maxErrors, rowNumber, HEADERS.get(2), "服务码数量不能为空");
            return null;
        }
        try {
            int quantity = new BigDecimal(value.trim()).intValueExact();
            if (quantity <= 0) {
                addError(errors, maxErrors, rowNumber, HEADERS.get(2), "服务码数量必须为正整数");
                return null;
            }
            return quantity;
        } catch (NumberFormatException | ArithmeticException exception) {
            addError(errors, maxErrors, rowNumber, HEADERS.get(2), "服务码数量必须为整数");
            return null;
        }
    }

    private LocalDateTime parseOrderTime(Cell cell, String value, DataFormatter formatter,
                                         List<OfflineImportError> errors, int rowNumber, int maxErrors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            try {
                return DateUtil.getLocalDateTime(cell.getNumericCellValue());
            } catch (RuntimeException exception) {
                addError(errors, maxErrors, rowNumber, HEADERS.get(3), "下单时间无效");
                return null;
            }
        }
        String text = value.trim();
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(text, SPACE_DATE_TIME);
            } catch (DateTimeParseException exception) {
                addError(errors, maxErrors, rowNumber, HEADERS.get(3),
                        "下单时间请使用 yyyy-MM-dd HH:mm:ss 或 ISO 本地时间格式");
                return null;
            }
        }
    }

    private boolean hasRowError(List<OfflineImportError> errors, int rowNumber) {
        return errors.stream().anyMatch(error -> error.row() == rowNumber);
    }

    private void addError(List<OfflineImportError> errors, int maxErrors, int row,
                          String field, String message) {
        if (errors.size() < maxErrors) {
            errors.add(new OfflineImportError(row, field, message));
        }
    }
}
