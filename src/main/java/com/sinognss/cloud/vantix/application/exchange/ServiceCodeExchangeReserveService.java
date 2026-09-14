package com.sinognss.cloud.vantix.application.exchange;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.domain.config.AccountConfig;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeBatch;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeDetail;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.infrastructure.mapper.AccountConfigMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeDetailMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ServiceCodeExchangeReserveService {
    private static final int INSERT_CHUNK_SIZE = 500;
    private static final String OPERATION_TYPE = "BATCH_CREATE_ACCOUNT";
    private static final String BIZ_TYPE = "EXCHANGE_BATCH";

    private final ExchangeBatchMapper batchMapper;
    private final ExchangeDetailMapper detailMapper;
    private final CorsOperationMapper operationMapper;
    private final ServiceCodeMapper serviceCodeMapper;
    private final ServiceDurationConfigMapper durationMapper;
    private final AccountConfigMapper accountConfigMapper;
    private final DealerCompanyMapper companyMapper;
    private final UserHolderBridge userHolder;
    private final GenerationProperties generationProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ServiceCodeExchangeReserveService(ExchangeBatchMapper batchMapper,
                                             ExchangeDetailMapper detailMapper,
                                             CorsOperationMapper operationMapper,
                                             ServiceCodeMapper serviceCodeMapper,
                                             ServiceDurationConfigMapper durationMapper,
                                             AccountConfigMapper accountConfigMapper,
                                             DealerCompanyMapper companyMapper,
                                             UserHolderBridge userHolder,
                                             GenerationProperties generationProperties,
                                             ObjectMapper objectMapper,
                                             Clock clock) {
        this.batchMapper = batchMapper;
        this.detailMapper = detailMapper;
        this.operationMapper = operationMapper;
        this.serviceCodeMapper = serviceCodeMapper;
        this.durationMapper = durationMapper;
        this.accountConfigMapper = accountConfigMapper;
        this.companyMapper = companyMapper;
        this.userHolder = userHolder;
        this.generationProperties = generationProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ExchangeReservation reserve(ServiceCodeExchangeCommand input) {
        ServiceCodeExchangeCommand command = normalize(input);
        validateMaxQuantity(command);
        UserScope scope = userHolder.getUserScope();
        assertCompanyAccess(scope, command.companyId());
        Long assignedUserId = effectiveAssignedUserId(scope);

        String payloadHash = ExchangePayloadHash.calculate(command, assignedUserId);
        ExchangeBatch existing = batchMapper.selectByRequestId(command.requestId());
        if (existing != null) {
            verifyPayload(existing, payloadHash, assignedUserId);
            CorsOperation operation = operationMapper.selectByBusiness(BIZ_TYPE, existing.getId());
            return new ExchangeReservation(existing.getId(), operation == null ? null : operation.getId(), false);
        }

        if (companyMapper.selectCount(Wrappers.<DealerCompany>lambdaQuery()
                .eq(DealerCompany::getCompanyId, command.companyId())) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公司不存在: " + command.companyId());
        }
        ServiceDurationConfig spec = durationMapper.selectBySpecCodes(List.of(command.specCode())).stream()
                .filter(item -> command.specCode().equals(item.getSpecCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_INVALID,
                        "服务码规格不存在: " + command.specCode()));
        AccountConfig accountConfig = accountConfigMapper.selectById(1L);
        if (accountConfig == null || accountConfig.getAccountSilenceMonths() == null
                || accountConfig.getAccountSilenceMonths() < 0) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "账号沉默配置无效");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        OperatorIdentity operator = userHolder.getOperator();
        ExchangeBatch batch = createBatch(command, spec, accountConfig, operator,
                payloadHash, assignedUserId, now);
        /*
         * Claim the requestId before locking any service codes. A concurrent retry with
         * the same requestId waits on this unique key, then reads the committed batch
         * instead of incorrectly seeing the codes as unavailable.
         */
        batchMapper.insert(batch);

        List<ServiceCode> codes = serviceCodeMapper.selectAvailableForExchange(
                command.companyId(), command.specCode(), command.generationSource().name(),
                now, command.quantity());
        if (codes.size() != command.quantity()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_SERVICE_CODES, "可兑换服务码数量不足");
        }
        validateSnapshots(codes, spec);

        List<ExchangeDetail> details = new ArrayList<>(codes.size());
        for (int index = 0; index < codes.size(); index++) {
            ServiceCode code = codes.get(index);
            ExchangeDetail detail = new ExchangeDetail();
            detail.setExchangeBatchId(batch.getId());
            detail.setDetailIndex(index + 1);
            detail.setServiceCodeId(code.getId());
            detail.setRequestId(command.requestId());
            detail.setServiceCodeSnapshot(serialize(new ExchangeCodeSnapshot(
                    code.getId(), code.getCode(), code.getOwnerCompanyId(), assignedUserId,
                    code.getServiceType(), code.getDurationValue(), code.getDurationUnit(),
                    code.getCodeSilenceMonths(), code.getExpireAt())));
            detail.setStatus(ExchangeStatus.PROCESSING);
            detail.setCreatedAt(now);
            detail.setUpdatedAt(now);
            details.add(detail);
        }
        for (int from = 0; from < details.size(); from += INSERT_CHUNK_SIZE) {
            List<ExchangeDetail> chunk = details.subList(from, Math.min(from + INSERT_CHUNK_SIZE, details.size()));
            if (detailMapper.insertBatch(chunk) != chunk.size()) {
                throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                        "兑换明细批量写入数量不一致");
            }
        }

        List<Long> serviceCodeIds = codes.stream().map(ServiceCode::getId).toList();
        if (serviceCodeMapper.reserveForExchange(serviceCodeIds, command.companyId(),
                command.requestId(), now) != command.quantity()) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "服务码预留状态发生并发变化");
        }

        CorsOperation operation = new CorsOperation();
        operation.setRequestId(command.requestId());
        operation.setOperationType(OPERATION_TYPE);
        operation.setBizType(BIZ_TYPE);
        operation.setBizId(batch.getId());
        operation.setStatus("PENDING");
        operation.setRetryCount(0);
        operation.setVersion(0L);
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);
        if (operationMapper.insert(operation) != 1) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT, "CORS 操作创建失败");
        }
        return new ExchangeReservation(batch.getId(), operation.getId(), true);
    }

    public ExchangeReservation findExisting(ServiceCodeExchangeCommand input) {
        ServiceCodeExchangeCommand command = normalize(input);
        validateMaxQuantity(command);
        UserScope scope = userHolder.getUserScope();
        assertCompanyAccess(scope, command.companyId());
        Long assignedUserId = effectiveAssignedUserId(scope);
        ExchangeBatch batch = batchMapper.selectByRequestId(command.requestId());
        if (batch == null) {
            return null;
        }
        verifyPayload(batch, ExchangePayloadHash.calculate(command, assignedUserId), assignedUserId);
        CorsOperation operation = operationMapper.selectByBusiness(BIZ_TYPE, batch.getId());
        return new ExchangeReservation(batch.getId(), operation == null ? null : operation.getId(), false);
    }

    private void validateMaxQuantity(ServiceCodeExchangeCommand command) {
        if (command.quantity() > generationProperties.getMaxQuantityPerRequest()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "单次兑换数量不能超过 " + generationProperties.getMaxQuantityPerRequest());
        }
    }

    static ServiceCodeExchangeCommand normalize(ServiceCodeExchangeCommand input) {
        if (input == null || input.requestId() == null || input.companyId() == null || input.companyId() <= 0
                || input.specCode() == null || input.generationSource() == null || input.quantity() == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "兑换参数不完整");
        }
        String requestId = input.requestId().trim();
        String specCode = input.specCode().trim();
        String accountPrefix = StringUtils.hasText(input.accountPrefix()) ? input.accountPrefix().trim() : null;
        if (!StringUtils.hasText(requestId) || requestId.length() > 128 || hasControl(requestId)
                || !StringUtils.hasText(specCode) || specCode.length() > 32 || hasControl(specCode)
                || input.quantity() <= 0 || (accountPrefix != null
                && (accountPrefix.length() > 64 || hasControl(accountPrefix)))) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "兑换参数非法");
        }
        return new ServiceCodeExchangeCommand(requestId, input.companyId(), specCode,
                input.generationSource(), input.quantity(), accountPrefix);
    }

    private ExchangeBatch createBatch(ServiceCodeExchangeCommand command, ServiceDurationConfig spec,
                                      AccountConfig accountConfig, OperatorIdentity operator,
                                      String payloadHash, Long assignedUserId, LocalDateTime now) {
        ExchangeBatch batch = new ExchangeBatch();
        batch.setExchangeBatchNo("EX-" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        batch.setRequestId(command.requestId());
        batch.setOwnerCompanyId(command.companyId());
        batch.setAssignedUserId(assignedUserId);
        batch.setGenerationSource(command.generationSource().name());
        batch.setSpecCode(command.specCode());
        batch.setServiceType(spec.getServiceType());
        batch.setDurationValue(spec.getDurationValue());
        batch.setDurationUnit(spec.getDurationUnit().name());
        batch.setQuantity(command.quantity());
        batch.setAccountPrefix(command.accountPrefix());
        batch.setPayloadHash(payloadHash);
        batch.setAccountSilenceMonths(accountConfig.getAccountSilenceMonths());
        batch.setStatus(ExchangeStatus.PROCESSING);
        batch.setOperatorUserId(operator.userId());
        batch.setOperatorUserName(operator.userName());
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        return batch;
    }

    private void validateSnapshots(List<ServiceCode> codes, ServiceDurationConfig spec) {
        for (ServiceCode code : codes) {
            if (code.getOwnerCompanyId() == null || code.getGenerateBatchId() == null
                    || !spec.getServiceType().equals(code.getServiceType())
                    || !spec.getDurationValue().equals(code.getDurationValue())
                    || !spec.getDurationUnit().name().equalsIgnoreCase(code.getDurationUnit())
                    || code.getCodeSilenceMonths() == null || code.getExpireAt() == null) {
                throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                        "待兑换服务码的规格快照与生成批次不一致");
            }
        }
    }

    private String serialize(ExchangeCodeSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "服务码兑换快照序列化失败");
        }
    }

    private static void verifyPayload(ExchangeBatch existing, String payloadHash, Long assignedUserId) {
        if (!java.util.Objects.equals(assignedUserId, existing.getAssignedUserId())
                || !payloadHash.equals(existing.getPayloadHash())) {
            throw new BusinessException(ErrorCode.EXCHANGE_IDEMPOTENCY_CONFLICT,
                    "requestId 已用于不同的兑换参数");
        }
    }

    private static void assertCompanyAccess(UserScope scope, Long companyId) {
        if (!scope.canAccessCompany(companyId)) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权兑换该公司的服务码");
        }
    }

    private static Long effectiveAssignedUserId(UserScope scope) {
        return scope.type() == UserScope.Type.PERSONAL ? scope.userId() : null;
    }

    private static boolean hasControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
