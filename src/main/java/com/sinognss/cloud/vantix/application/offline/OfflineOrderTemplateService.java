package com.sinognss.cloud.vantix.application.offline;

import com.sinognss.cloud.vantix.config.OfflineImportProperties;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class OfflineOrderTemplateService {
    private static final String DURATION_SHEET = "可选服务时长";
    private final ServiceDurationConfigMapper configMapper;
    private final OfflineImportProperties properties;

    public OfflineOrderTemplateService(ServiceDurationConfigMapper configMapper,
                                       OfflineImportProperties properties) {
        this.configMapper = configMapper;
        this.properties = properties;
    }

    public byte[] createTemplate() {
        List<ServiceDurationConfig> enabled = configMapper.selectEnabled();
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet orders = workbook.createSheet(OfflineOrderExcelParser.MAIN_SHEET);
            Sheet durations = workbook.createSheet(DURATION_SHEET);
            Sheet instructions = workbook.createSheet("填写说明");

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            var headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            Row header = orders.createRow(0);
            for (int index = 0; index < OfflineOrderExcelParser.HEADERS.size(); index++) {
                var cell = header.createCell(index);
                cell.setCellValue(OfflineOrderExcelParser.HEADERS.get(index));
                cell.setCellStyle(headerStyle);
                orders.setColumnWidth(index, index == 4 ? 36 * 256 : 24 * 256);
            }

            durations.createRow(0).createCell(0).setCellValue("服务时长");
            durations.getRow(0).createCell(1).setCellValue("specCode（系统辅助）");
            for (int index = 0; index < enabled.size(); index++) {
                Row row = durations.createRow(index + 1);
                row.createCell(0).setCellValue(com.sinognss.cloud.vantix.common.DurationDisplayFormatter
                        .format(enabled.get(index).getDurationValue(), enabled.get(index).getDurationUnit()));
                row.createCell(1).setCellValue(enabled.get(index).getSpecCode());
            }
            durations.setColumnHidden(1, true);
            durations.setColumnWidth(0, 24 * 256);
            Name durationNames = workbook.createName();
            durationNames.setNameName("ServiceDurationDisplayNames");
            if (enabled.isEmpty()) {
                durations.createRow(1).createCell(0).setCellValue("暂无可用服务时长");
                durationNames.setRefersToFormula("'" + DURATION_SHEET + "'!$A$2:$A$2");
            } else {
                durationNames.setRefersToFormula("'" + DURATION_SHEET + "'!$A$2:$A$" + (enabled.size() + 1));
            }

            DataValidationHelper helper = orders.getDataValidationHelper();
            DataValidationConstraint listConstraint = helper.createFormulaListConstraint("ServiceDurationDisplayNames");
            DataValidation listValidation = helper.createValidation(listConstraint,
                    new org.apache.poi.ss.util.CellRangeAddressList(1, properties.getMaxRows(), 1, 1));
            listValidation.setShowErrorBox(true);
            listValidation.createErrorBox("服务时长无效", "请从下拉列表选择当前启用的服务时长");
            orders.addValidationData(listValidation);

            DataValidationConstraint quantityConstraint = helper.createIntegerConstraint(
                    DataValidationConstraint.OperatorType.BETWEEN, "1",
                    Integer.toString(properties.getMaxTotalCodes()));
            DataValidation quantityValidation = helper.createValidation(quantityConstraint,
                    new org.apache.poi.ss.util.CellRangeAddressList(1, properties.getMaxRows(), 2, 2));
            quantityValidation.setShowErrorBox(true);
            quantityValidation.createErrorBox("数量无效", "请输入正整数");
            orders.addValidationData(quantityValidation);

            instructions.setColumnWidth(0, 100 * 256);
            String[] notes = {
                    "一次文件只属于页面当前选择的一家公司，公司信息不填写在 Excel。",
                    "订单号必填；服务时长只能选择模板中的当前启用项；服务码数量必须为正整数。",
                    "重复订单规格不会重复生成；已导入的订单规格会使整份文件被拒绝。",
                    "不要修改模板表头或添加公司、商品、部件号、服务类型、金额、配置 ID 等字段。",
                    "下单时间可留空；填写时使用 yyyy-MM-dd HH:mm:ss、ISO 本地时间，或 Excel 日期单元格。",
                    "文件最多 " + properties.getMaxRows() + " 行，最多生成 " + properties.getMaxTotalCodes() + " 个服务码。"
            };
            for (int index = 0; index < notes.length; index++) {
                instructions.createRow(index).createCell(0).setCellValue(notes[index]);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("线下订单模板生成失败", exception);
        }
    }
}