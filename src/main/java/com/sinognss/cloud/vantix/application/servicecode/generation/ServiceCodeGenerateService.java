package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sinognss.cloud.vantix.common.ServiceCodeGenerator;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateBatch;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class ServiceCodeGenerateService {
    private static final int UNIQUE_RETRY_LIMIT = 5;

    private final ServiceCodeGenerateBatchMapper batchMapper;
    private final ServiceCodeMapper codeMapper;
    private final ServiceDurationConfigMapper durationConfigMapper;
    private final DealerCompanyMapper companyMapper;
    private final ServiceCodeGenerator codeGenerator;
    private final GenerationProperties properties;
    private final Clock clock;

    public ServiceCodeGenerateService(ServiceCodeGenerateBatchMapper batchMapper,
                                      ServiceCodeMapper codeMapper,
                                      ServiceDurationConfigMapper durationConfigMapper,
                                      DealerCompanyMapper companyMapper,
                                      ServiceCodeGenerator codeGenerator,
                                      GenerationProperties properties,
                                      Clock clock) {
        this.batchMapper = batchMapper;
        this.codeMapper = codeMapper;
        this.durationConfigMapper = durationConfigMapper;
        this.companyMapper = companyMapper;
        this.codeGenerator = codeGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public GenerateServiceCodeResult generate(GenerateServiceCodeCommand command, OperatorIdentity operator) {
        validate(command, operator);
        String requestId = command.requestId().trim();
        String orderNo = command.orderNo().trim();
        String specCode = command.specCode().trim();
        String businessHash = businessKeyHash(command.generationSource(), command.companyId(), orderNo, specCode);

        ServiceCodeGenerateBatch byRequest = batchMapper.selectByRequestIdForUpdate(requestId);
        if (byRequest != null) {
            if (!businessHash.equals(byRequest.getBusinessKeyHash())
                    || !command.generationSource().equals(byRequest.getGenerationSource())
                    || !command.companyId().equals(byRequest.getOwnerCompanyId())
                    || !command.quantity().equals(byRequest.getQuantity())) {
                throw new BusinessException(ErrorCode.GENERATION_IDEMPOTENCY_CONFLICT,
                        "requestId 已用于不同的服务码生成请求");
            }
            return result(byRequest, true);
        }

        ServiceCodeGenerateBatch byBusinessKey = batchMapper.selectByBusinessKeyForUpdate(
                command.generationSource().name(), command.companyId(), businessHash);
        if (byBusinessKey != null) {
            if (command.generationSource() == GenerationSource.OFFLINE) {
                throw new BusinessException(ErrorCode.GENERATION_ALREADY_EXISTS,
                        "该订单规格已经导入: " + orderNo + " / " + specCode);
            }
            return result(byBusinessKey, true);
        }

        if (companyMapper.selectCount(Wrappers.<DealerCompany>lambdaQuery()
                .eq(DealerCompany::getCompanyId, command.companyId())) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公司不存在: " + command.companyId());
        }
        ServiceDurationConfig spec = durationConfigMapper.selectEnabledBySpecCode(specCode);
        if (spec == null) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "服务码规格不存在或已停用: " + specCode);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ServiceCodeGenerateBatch batch = new ServiceCodeGenerateBatch();
        batch.setRequestId(requestId);
        batch.setGenerationSource(command.generationSource());
        batch.setSourceOrderNo(orderNo);
        batch.setSourceOrderTime(command.orderTime());
        batch.setOwnerCompanyId(command.companyId());
        batch.setSpecCode(spec.getSpecCode());
        batch.setDurationValue(spec.getDurationValue());
        batch.setDurationUnit(spec.getDurationUnit().name());
        batch.setCodeSilenceMonths(spec.getCodeSilenceMonths());
        batch.setQuantity(command.quantity());
        batch.setGeneratedCount(0);
        batch.setStatus("GENERATING");
        batch.setRemark(command.remark() == null || command.remark().isBlank() ? null : command.remark().trim());
        batch.setOperatorUserId(operator.userId());
        batch.setOperatorUserName(operator.userName());
        batch.setBusinessKeyHash(businessHash);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        insertBatch(batch, requestId, command, businessHash, orderNo);

        List<String> generatedCodes = new ArrayList<>(command.quantity());
        for (int index = 0; index < command.quantity(); index++) {
            generatedCodes.add(insertCode(batch, spec, now));
        }
        if (batchMapper.complete(batch.getId(), generatedCodes.size(), LocalDateTime.now(clock)) != 1) {
            throw new BusinessException(ErrorCode.BATCH_STATUS_INCONSISTENT, "服务码生成批次状态更新失败");
        }
        batch.setGeneratedCount(generatedCodes.size());
        batch.setStatus("COMPLETED");
        return new GenerateServiceCodeResult(ServiceCodeBatchView.from(batch), false, List.copyOf(generatedCodes));
    }

    private void insertBatch(ServiceCodeGenerateBatch batch, String requestId,
                             GenerateServiceCodeCommand command, String businessHash, String orderNo) {
        for (int attempt = 0; attempt < UNIQUE_RETRY_LIMIT; attempt++) {
            batch.setBatchNo(codeGenerator.generateBatchNo(LocalDateTime.now(clock).toLocalDate()));
            try {
                batchMapper.insert(batch);
                return;
            } catch (DuplicateKeyException exception) {
                ServiceCodeGenerateBatch existingRequest = batchMapper.selectByRequestIdForUpdate(requestId);
                if (existingRequest != null) {
                    if (businessHash.equals(existingRequest.getBusinessKeyHash())
                            && command.quantity().equals(existingRequest.getQuantity())) {
                        throw new BusinessException(ErrorCode.GENERATION_CONCURRENT_RETRY,
                                "请求正在并发生成，请使用同一 requestId 重试");
                    }
                    throw new BusinessException(ErrorCode.GENERATION_IDEMPOTENCY_CONFLICT,
                            "requestId 已用于不同的服务码生成请求");
                }
                ServiceCodeGenerateBatch existingBusiness = batchMapper.selectByBusinessKeyForUpdate(
                        command.generationSource().name(), command.companyId(), businessHash);
                if (existingBusiness != null) {
                    if (command.generationSource() == GenerationSource.OFFLINE) {
                        throw new BusinessException(ErrorCode.GENERATION_ALREADY_EXISTS,
                                "该订单规格已经导入: " + orderNo);
                    }
                    throw new BusinessException(ErrorCode.GENERATION_CONCURRENT_RETRY,
                            "该订单规格正在并发生成，请稍后重试");
                }
                if (attempt + 1 == UNIQUE_RETRY_LIMIT) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生成批次编号冲突，请稍后重试");
                }
            }
        }
    }

    private String insertCode(ServiceCodeGenerateBatch batch, ServiceDurationConfig spec, LocalDateTime now) {
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
                return code.getCode();
            } catch (DuplicateKeyException exception) {
                if (attempt + 1 == UNIQUE_RETRY_LIMIT) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "服务码唯一编号冲突次数过多，生成批次已回滚");
                }
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "服务码生成失败");
    }

    private GenerateServiceCodeResult result(ServiceCodeGenerateBatch batch, boolean idempotent) {
        List<String> codes = codeMapper.selectByGenerateBatchId(batch.getId())
                .stream().map(ServiceCode::getCode).toList();
        return new GenerateServiceCodeResult(ServiceCodeBatchView.from(batch), idempotent, codes);
    }

    private void validate(GenerateServiceCodeCommand command, OperatorIdentity operator) {
        if (command == null || command.generationSource() == null || command.requestId() == null
                || command.requestId().isBlank() || command.requestId().length() > 160
                || command.orderNo() == null || command.orderNo().isBlank() || command.orderNo().trim().length() > 128
                || command.orderNo().codePoints().anyMatch(Character::isISOControl)
                || command.companyId() == null || command.companyId() <= 0
                || command.specCode() == null || command.specCode().isBlank()
                || command.specCode().length() > 700
                || command.quantity() == null || command.quantity() <= 0
                || command.quantity() > properties.getMaxQuantityPerRequest()
                || (command.remark() != null && command.remark().length() > 512)
                || operator == null || (operator.userName() != null && operator.userName().length() > 128)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "服务码生成参数非法");
        }
    }

    public static String businessKeyHash(GenerationSource source, Long companyId, String orderNo, String specCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            putField(digest, source.name());
            putField(digest, companyId.toString());
            putField(digest, orderNo);
            putField(digest, specCode);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void putField(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}