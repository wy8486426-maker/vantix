package com.sinognss.cloud.vantix.application.offline;

import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeResult;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.config.OfflineImportProperties;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OfflineOrderImportService {
    private final OfflineOrderExcelParser parser;
    private final OfflineOrderTemplateService templateService;
    private final OfflineOrderImportTransaction importTransaction;
    private final ServiceDurationConfigMapper durationConfigMapper;
    private final ServiceCodeGenerateBatchMapper batchMapper;
    private final DealerCompanyMapper companyMapper;
    private final UserHolderBridge userHolder;
    private final OfflineImportProperties properties;
    private final GenerationProperties generationProperties;

    public OfflineOrderImportService(OfflineOrderExcelParser parser,
                                     OfflineOrderTemplateService templateService,
                                     OfflineOrderImportTransaction importTransaction,
                                     ServiceDurationConfigMapper durationConfigMapper,
                                     ServiceCodeGenerateBatchMapper batchMapper,
                                     DealerCompanyMapper companyMapper,
                                     UserHolderBridge userHolder,
                                     OfflineImportProperties properties,
                                     GenerationProperties generationProperties) {
        this.parser = parser;
        this.templateService = templateService;
        this.importTransaction = importTransaction;
        this.durationConfigMapper = durationConfigMapper;
        this.batchMapper = batchMapper;
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
        if (companyMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers
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
        int totalCodes = 0;
        for (ParsedOfflineOrder order : parsed.rows()) {
            String key = order.orderNo() + "\u0000" + order.specCode();
            ParsedOfflineOrder first = firstRowsByBusinessKey.putIfAbsent(key, order);
            if (first != null) {
                addError(errors, order.rowNumber(), "订单号/服务时长",
                        "与第 " + first.rowNumber() + " 行重复：" + order.orderNo() + " / " + order.displayName());
            }
            if (order.quantity() > generationProperties.getMaxQuantityPerRequest()) {
                addError(errors, order.rowNumber(), "服务码数量",
                        "单行数量不能超过 " + generationProperties.getMaxQuantityPerRequest());
            }
            if (order.quantity() > properties.getMaxTotalCodes() - totalCodes) {
                addError(errors, order.rowNumber(), "服务码数量",
                        "文件服务码总量不能超过 " + properties.getMaxTotalCodes());
            } else {
                totalCodes += order.quantity();
            }
            String hash = com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeGenerateService
                    .businessKeyHash(GenerationSource.OFFLINE, companyId, order.orderNo(), order.specCode());
            if (batchMapper.selectByBusinessKey(GenerationSource.OFFLINE.name(), companyId, hash) != null) {
                addError(errors, order.rowNumber(), "订单号/服务时长",
                        "该订单的 " + order.displayName() + " 服务码已经导入");
            }
        }
        if (!errors.isEmpty()) {
            throw new OfflineImportValidationException(List.copyOf(errors));
        }

        List<GenerateServiceCodeResult> results = importTransaction.generateAll(
                companyId, parsed.rows(), operator);
        return new OfflineImportResult(results.size(), results.stream()
                .mapToInt(result -> result.batch().generatedCount()).sum(),
                results.stream().map(GenerateServiceCodeResult::batch).toList());
    }

    private void addError(List<OfflineImportError> errors, int row, String field, String message) {
        if (errors.size() < properties.getMaxErrors()) {
            errors.add(new OfflineImportError(row, field, message));
        }
    }
}