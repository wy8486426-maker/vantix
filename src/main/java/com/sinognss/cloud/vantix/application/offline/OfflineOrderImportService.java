package com.sinognss.cloud.vantix.application.offline;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeItemCommand;
import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeOrderCommand;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeGenerateOrderView;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeOrderGenerateService;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeGenerateOrderItemView;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeBatchView;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.config.OfflineImportProperties;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateOrder;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateOrderMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OfflineOrderImportService {
    private final OfflineOrderExcelParser parser;
    private final OfflineOrderTemplateService templateService;
    private final OfflineOrderImportTransaction importTransaction;
    private final ServiceDurationConfigMapper durationConfigMapper;
    private final ServiceCodeGenerateOrderMapper orderMapper;
    private final DealerCompanyMapper companyMapper;
    private final UserHolderBridge userHolder;
    private final OfflineImportProperties properties;
    private final GenerationProperties generationProperties;

    public OfflineOrderImportService(OfflineOrderExcelParser parser,
                                     OfflineOrderTemplateService templateService,
                                     OfflineOrderImportTransaction importTransaction,
                                     ServiceDurationConfigMapper durationConfigMapper,
                                     ServiceCodeGenerateOrderMapper orderMapper,
                                     DealerCompanyMapper companyMapper,
                                     UserHolderBridge userHolder,
                                     OfflineImportProperties properties,
                                     GenerationProperties generationProperties) {
        this.parser = parser;
        this.templateService = templateService;
        this.importTransaction = importTransaction;
        this.durationConfigMapper = durationConfigMapper;
        this.orderMapper = orderMapper;
        this.companyMapper = companyMapper;
        this.userHolder = userHolder;
        this.properties = properties;
        this.generationProperties = generationProperties;
    }

    public byte[] createTemplate() {
        return templateService.createTemplate();
    }

    public OfflineImportResult importFile(Long companyId, MultipartFile file) {
        List<OfflineImportError> errors = new ArrayList<>();
        if (companyId == null || companyId <= 0) {
            addError(errors, 0, "companyId", "必须选择有效公司");
        } else if (!userHolder.getUserScope().canAccessCompany(companyId)) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权向该公司导入服务码");
        }
        OperatorIdentity operator = userHolder.getOperator();
        if (file == null || file.isEmpty()) {
            addError(errors, 0, "文件", "请上传 .xlsx 文件");
        } else {
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
                addError(errors, 0, "文件", "仅支持 .xlsx 文件");
            }
            if (file.getSize() > properties.getMaxFileSizeBytes()) {
                addError(errors, 0, "文件", "文件大小超过上限 " + properties.getMaxFileSizeBytes() + " 字节");
            }
        }
        if (!errors.isEmpty()) {
            throw new OfflineImportValidationException(errors);
        }
        if (companyMapper.selectCount(Wrappers
                .<com.sinognss.cloud.vantix.domain.company.DealerCompany>lambdaQuery()
                .eq(com.sinognss.cloud.vantix.domain.company.DealerCompany::getCompanyId, companyId)) == 0) {
            throw new OfflineImportValidationException(List.of(
                    new OfflineImportError(0, "companyId", "公司不存在：" + companyId)));
        }

        OfflineParseResult parsed;
        try {
            parsed = parser.parse(file.getInputStream(), durationConfigMapper.selectEnabled(),
                    properties.getMaxRows(), properties.getMaxErrors());
        } catch (IOException exception) {
            throw new OfflineImportValidationException(List.of(
                    new OfflineImportError(0, "文件", "无法读取上传文件")));
        }
        errors.addAll(parsed.errors());
        if (errors.size() > properties.getMaxErrors()) {
            errors = new ArrayList<>(errors.subList(0, properties.getMaxErrors()));
        }

        Map<String, ParsedOfflineOrder> firstRowsByBusinessKey = new HashMap<>();
        Map<String, List<ParsedOfflineOrder>> rowsByOrderNo = new LinkedHashMap<>();
        int totalCodes = 0;
        for (ParsedOfflineOrder row : parsed.rows()) {
            String businessKey = row.orderNo() + "\u0000" + row.specCode();
            ParsedOfflineOrder first = firstRowsByBusinessKey.putIfAbsent(businessKey, row);
            if (first != null) {
                addError(errors, row.rowNumber(), "订单号/服务时长",
                        "与第 " + first.rowNumber() + " 行重复：" + row.orderNo() + " / " + row.displayName());
            }
            if (row.quantity() > generationProperties.getMaxQuantityPerRequest()) {
                addError(errors, row.rowNumber(), "服务码数量",
                        "单行数量不能超过 " + generationProperties.getMaxQuantityPerRequest());
            }
            if (row.quantity() > properties.getMaxTotalCodes() - totalCodes) {
                addError(errors, row.rowNumber(), "服务码数量",
                        "文件服务码总量不能超过 " + properties.getMaxTotalCodes());
            } else {
                totalCodes += row.quantity();
            }
            rowsByOrderNo.computeIfAbsent(row.orderNo(), ignored -> new ArrayList<>()).add(row);
        }

        List<ParsedOfflineOrderGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<ParsedOfflineOrder>> entry : rowsByOrderNo.entrySet()) {
            List<ParsedOfflineOrder> rows = entry.getValue();
            ParsedOfflineOrder firstRow = rows.get(0);
            java.time.LocalDateTime orderTime = null;
            for (ParsedOfflineOrder row : rows) {
                if (row.orderTime() != null) {
                    if (orderTime != null && !orderTime.equals(row.orderTime())) {
                        addError(errors, row.rowNumber(), "下单时间",
                                "同一订单的下单时间必须一致：" + entry.getKey());
                    } else {
                        orderTime = row.orderTime();
                    }
                }
            }
            if (rows.size() > generationProperties.getMaxItemsPerOrder()) {
                addError(errors, firstRow.rowNumber(), "服务时长",
                        "同一订单规格数不能超过 " + generationProperties.getMaxItemsPerOrder());
            }
            long orderQuantity = rows.stream().mapToLong(ParsedOfflineOrder::quantity).sum();
            if (orderQuantity > generationProperties.getMaxTotalQuantityPerOrder()) {
                addError(errors, firstRow.rowNumber(), "服务码数量",
                        "同一订单服务码总量不能超过 " + generationProperties.getMaxTotalQuantityPerOrder());
            }

            List<GenerateServiceCodeItemCommand> items = rows.stream()
                    .map(row -> new GenerateServiceCodeItemCommand(row.specCode(), row.quantity(), row.remark()))
                    .toList();
            String requestId = ServiceCodeOrderGenerateService.offlineRequestId(companyId, entry.getKey());
            GenerateServiceCodeOrderCommand command = new GenerateServiceCodeOrderCommand(
                    GenerationSource.OFFLINE, requestId, entry.getKey(), orderTime, companyId, items);
            ServiceCodeGenerateOrder existing = orderMapper.selectByBusinessKey(
                    GenerationSource.OFFLINE.name(), companyId, entry.getKey());
            if (existing != null) {
                if (ServiceCodeOrderGenerateService.payloadHash(command).equals(existing.getPayloadHash())) {
                    addError(errors, firstRow.rowNumber(), "订单号", "该订单已经导入：" + entry.getKey());
                } else {
                    addError(errors, firstRow.rowNumber(), "订单号", "订单内容与历史导入不一致：" + entry.getKey());
                }
            }
            groups.add(new ParsedOfflineOrderGroup(firstRow.rowNumber(), entry.getKey(), orderTime, List.copyOf(rows)));
        }
        if (!errors.isEmpty()) {
            throw new OfflineImportValidationException(List.copyOf(errors));
        }

        List<ServiceCodeGenerateOrderView> results = importTransaction.generateAll(companyId, groups, operator);
        List<ServiceCodeBatchView> batches = results.stream()
                .flatMap(order -> order.items().stream().map(item -> toBatchView(order, item)))
                .toList();
        return new OfflineImportResult(batches.size(),
                results.stream().mapToInt(ServiceCodeGenerateOrderView::totalQuantity).sum(), batches);
    }

    private ServiceCodeBatchView toBatchView(ServiceCodeGenerateOrderView order,
                                              ServiceCodeGenerateOrderItemView item) {
        return new ServiceCodeBatchView(item.batchNo(), order.generationSource(), order.orderNo(),
                order.companyId(), item.specCode(), item.displayName(), item.quantity(),
                item.generatedCount(), item.status(), order.createdAt());
    }


    private void addError(List<OfflineImportError> errors, int row, String field, String message) {
        if (errors.size() < properties.getMaxErrors()) {
            errors.add(new OfflineImportError(row, field, message));
        }
    }
}