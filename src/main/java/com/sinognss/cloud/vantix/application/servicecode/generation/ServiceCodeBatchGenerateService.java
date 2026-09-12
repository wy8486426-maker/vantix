package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.sinognss.cloud.vantix.common.ServiceCodeGenerator;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateBatch;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ServiceCodeBatchGenerateService {
    private static final int UNIQUE_RETRY_LIMIT = 5;

    private final ServiceCodeGenerateBatchMapper batchMapper;
    private final ServiceCodeMapper codeMapper;
    private final ServiceCodeGenerator codeGenerator;
    private final Clock clock;

    public ServiceCodeBatchGenerateService(ServiceCodeGenerateBatchMapper batchMapper,
                                           ServiceCodeMapper codeMapper,
                                           ServiceCodeGenerator codeGenerator,
                                           Clock clock) {
        this.batchMapper = batchMapper;
        this.codeMapper = codeMapper;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ServiceCodeBatchView generateBatch(Long generateOrderId,
                                              GenerateServiceCodeOrderCommand order,
                                              GenerateServiceCodeItemCommand item,
                                              ServiceDurationConfig spec,
                                              OperatorIdentity operator) {
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
        batch.setDurationValue(spec.getDurationValue());
        batch.setDurationUnit(spec.getDurationUnit().name());
        batch.setCodeSilenceMonths(spec.getCodeSilenceMonths());
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
        for (int index = 0; index < item.quantity(); index++) {
            insertCode(batch, spec, now);
        }
        if (batchMapper.complete(batch.getId(), item.quantity(), LocalDateTime.now(clock)) != 1) {
            throw new BusinessException(ErrorCode.BATCH_STATUS_INCONSISTENT, "服务码生成批次状态更新失败");
        }
        batch.setGeneratedCount(item.quantity());
        batch.setStatus("COMPLETED");
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

    private void insertCode(ServiceCodeGenerateBatch batch, ServiceDurationConfig spec, LocalDateTime now) {
        for (int attempt = 0; attempt < UNIQUE_RETRY_LIMIT; attempt++) {
            ServiceCode code = new ServiceCode();
            code.setCode(codeGenerator.generate(now.toLocalDate()));
            code.setSourceOrderNo(batch.getSourceOrderNo());
            code.setGenerateBatchId(batch.getId());
            code.setOwnerCompanyId(batch.getOwnerCompanyId());
            code.setServiceType(spec.getServiceType());
            code.setDurationValue(spec.getDurationValue());
            code.setDurationUnit(spec.getDurationUnit().name());
            code.setCodeSilenceMonths(spec.getCodeSilenceMonths());
            code.setExpireAt(now.plusMonths(spec.getCodeSilenceMonths()));
            code.setStatus(ServiceCodeStatus.PENDING);
            code.setVersion(0L);
            code.setCreatedAt(now);
            code.setUpdatedAt(now);
            try {
                codeMapper.insert(code);
                return;
            } catch (DuplicateKeyException exception) {
                if (attempt + 1 == UNIQUE_RETRY_LIMIT) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "服务码唯一编号冲突次数过多，整个订单已回滚");
                }
            }
        }
    }
}
