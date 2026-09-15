package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.sinognss.cloud.vantix.common.ServiceCodeGenerator;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateBatch;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ServiceCodeBatchGenerateService {
    private static final Logger log = LoggerFactory.getLogger(ServiceCodeBatchGenerateService.class);
    private static final int UNIQUE_RETRY_LIMIT = 5;

    private final ServiceCodeGenerateBatchMapper batchMapper;
    private final ServiceCodeMapper codeMapper;
    private final ServiceCodeGenerator codeGenerator;
    private final GenerationProperties properties;
    private final Clock clock;

    public ServiceCodeBatchGenerateService(ServiceCodeGenerateBatchMapper batchMapper,
                                           ServiceCodeMapper codeMapper,
                                           ServiceCodeGenerator codeGenerator,
                                           GenerationProperties properties,
                                           Clock clock) {
        this.batchMapper = batchMapper;
        this.codeMapper = codeMapper;
        this.codeGenerator = codeGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ServiceCodeBatchView generateBatch(Long generateOrderId,
                                              GenerateServiceCodeOrderCommand order,
                                              GenerateServiceCodeItemCommand item,
                                              ServiceDurationConfig spec,
                                              OperatorIdentity operator,
                                              Set<String> generatedCodes) {
        long startedAt = System.nanoTime();
        LocalDateTime now = LocalDateTime.now(clock);
        String specCode = item.specCode().trim();
        String businessHash = GenerationHash.businessKey(order.generationSource(),
                order.companyId(), order.orderNo().trim(), specCode);

        ServiceCodeGenerateBatch batch = new ServiceCodeGenerateBatch();
        batch.setRequestId(GenerationHash.childRequestId(order.requestId().trim(), specCode));
        batch.setGenerationSource(order.generationSource());
        batch.setSourceOrderNo(order.orderNo().trim());
        batch.setSourceOrderTime(order.orderTime());
        batch.setOwnerCompanyId(order.companyId());
        batch.setSpecCode(spec.getSpecCode());
        batch.setDisplayName(spec.getDisplayName());
        batch.setServiceType(spec.getServiceType());
        batch.setDurationDays(spec.getDurationDays());
        batch.setCodeSilenceDays(spec.getCodeSilenceDays());
        batch.setQuantity(item.quantity());
        batch.setGeneratedCount(0);
        batch.setStatus("GENERATING");
        batch.setRemark(item.remark() == null || item.remark().isBlank() ? null : item.remark().trim());
        batch.setOperatorUserId(operator.userId());
        batch.setOperatorUserName(operator.userName());
        batch.setBusinessKeyHash(businessHash);
        batch.setGenerateOrderId(generateOrderId);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);

        insertBatch(batch);
        List<ServiceCode> codes = generateCodes(batch, spec, now, item.quantity(), generatedCodes);
        int batchSize = properties.getBatchInsertSize();
        int chunkCount = (codes.size() + batchSize - 1) / batchSize;
        for (int from = 0; from < codes.size(); from += batchSize) {
            int to = Math.min(from + batchSize, codes.size());
            try {
                codeMapper.insertBatch(codes.subList(from, to));
            } catch (DuplicateKeyException exception) {
                log.warn("Service-code batch insert conflicted, orderId={}, batchId={}, specCode={}, quantity={}",
                        generateOrderId, batch.getId(), specCode, item.quantity());
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "服务码批量写入发生唯一约束冲突，整个订单已回滚");
            }
        }
        if (batchMapper.complete(batch.getId(), item.quantity(), LocalDateTime.now(clock)) != 1) {
            throw new BusinessException(ErrorCode.BATCH_STATUS_INCONSISTENT, "服务码生成批次状态更新失败");
        }
        batch.setGeneratedCount(item.quantity());
        batch.setStatus("COMPLETED");
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("Generated service codes, orderId={}, batchId={}, specCode={}, quantity={}, chunks={}, elapsedMs={}",
                generateOrderId, batch.getId(), specCode, item.quantity(), chunkCount, elapsedMs);
        return ServiceCodeBatchView.from(batch);
    }

    private void insertBatch(ServiceCodeGenerateBatch batch) {
        for (int attempt = 0; attempt < UNIQUE_RETRY_LIMIT; attempt++) {
            batch.setBatchNo(codeGenerator.generateBatchNo(LocalDateTime.now(clock).toLocalDate()));
            try {
                batchMapper.insert(batch);
                return;
            } catch (DuplicateKeyException exception) {
                if (attempt + 1 == UNIQUE_RETRY_LIMIT) {
                    throw new BusinessException(ErrorCode.GENERATION_CONCURRENT_RETRY,
                            "订单规格批次冲突，请使用同一 requestId 重试");
                }
            }
        }
    }

    private List<ServiceCode> generateCodes(ServiceCodeGenerateBatch batch,
                                            ServiceDurationConfig spec,
                                            LocalDateTime now,
                                            int quantity,
                                            Set<String> generatedCodes) {
        List<ServiceCode> codes = new ArrayList<>(quantity);
        for (int index = 0; index < quantity; index++) {
            ServiceCode code = new ServiceCode();
            boolean unique = false;
            for (int attempt = 0; attempt < UNIQUE_RETRY_LIMIT; attempt++) {
                String candidate = codeGenerator.generate(now.toLocalDate());
                if (candidate != null && generatedCodes.add(candidate)) {
                    code.setCode(candidate);
                    unique = true;
                    break;
                }
            }
            if (!unique) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "服务码订单内唯一编号生成失败，整个订单已回滚");
            }
            code.setSourceOrderNo(batch.getSourceOrderNo());
            code.setGenerateBatchId(batch.getId());
            code.setOwnerCompanyId(batch.getOwnerCompanyId());
            code.setSpecCode(spec.getSpecCode());
            code.setServiceType(spec.getServiceType());
            code.setDurationDays(spec.getDurationDays());
            code.setCodeSilenceDays(spec.getCodeSilenceDays());
            code.setExpireAt(now.plusDays(spec.getCodeSilenceDays()));
            code.setStatus(ServiceCodeStatus.PENDING);
            code.setVersion(0L);
            code.setCreatedAt(now);
            code.setUpdatedAt(now);
            codes.add(code);
        }
        return codes;
    }
}
