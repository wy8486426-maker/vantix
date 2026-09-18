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
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeBatch;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeDetail;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeStatus;
import com.sinognss.cloud.vantix.domain.exchange.CompanyExchangeConfig;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ExchangeDetailMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.CompanyExchangeConfigMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ServiceCodeExchangeReserveService {
    private static final int INSERT_CHUNK_SIZE = 500;
    private static final int MAX_EXACT_CODES = 500;
    private static final String OPERATION_TYPE = "BATCH_CREATE_ACCOUNT";
    private static final String BIZ_TYPE = "EXCHANGE_BATCH";
    static final String EXACT_GENERATION_SOURCE = "EXACT";

    private final ExchangeBatchMapper batchMapper;
    private final ExchangeDetailMapper detailMapper;
    private final CompanyExchangeConfigMapper exchangeConfigMapper;
    private final CorsOperationMapper operationMapper;
    private final ServiceCodeMapper serviceCodeMapper;
    private final ServiceDurationConfigMapper durationMapper;
    private final DealerCompanyMapper companyMapper;
    private final UserHolderBridge userHolder;
    private final GenerationProperties generationProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ServiceCodeExchangeReserveService(ExchangeBatchMapper batchMapper,
                                             ExchangeDetailMapper detailMapper,
                                             CompanyExchangeConfigMapper exchangeConfigMapper,
                                             CorsOperationMapper operationMapper,
                                             ServiceCodeMapper serviceCodeMapper,
                                             ServiceDurationConfigMapper durationMapper,
                                             DealerCompanyMapper companyMapper,
                                             UserHolderBridge userHolder,
                                             GenerationProperties generationProperties,
                                             ObjectMapper objectMapper,
                                             Clock clock) {
        this.batchMapper = batchMapper;
        this.detailMapper = detailMapper;
        this.exchangeConfigMapper = exchangeConfigMapper;
        this.operationMapper = operationMapper;
        this.serviceCodeMapper = serviceCodeMapper;
        this.durationMapper = durationMapper;
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
        CompanyExchangeConfig exchangeConfig = exchangeConfigMapper.selectByCompanyId(command.companyId());
        if (exchangeConfig == null) {
            throw new BusinessException(ErrorCode.EXCHANGE_CONFIG_REQUIRED,
                    "请先配置兑换账号前缀");
        }
        ServiceDurationConfig spec = durationMapper.selectBySpecCodes(List.of(command.specCode())).stream()
                .filter(item -> command.specCode().equals(item.getSpecCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_INVALID,
                        "服务码规格不存在: " + command.specCode()));
        if (spec.getDurationDays() == null || spec.getDurationDays() <= 0
                || spec.getAccountSilenceDays() == null || spec.getAccountSilenceDays() < 0) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "账号沉默配置无效");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        OperatorIdentity operator = userHolder.getOperator();
        ExchangeBatch batch = createBatch(command, spec, operator,
                exchangeConfig.getAccountPrefix(), payloadHash, assignedUserId, now);
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
        return persistSelectedCodes(batch, codes, assignedUserId, spec, now, true);
    }

    @Transactional
    public ExchangeReservation reserveByCodes(ServiceCodeExchangeByCodesCommand input) {
        ServiceCodeExchangeByCodesCommand command = normalizeByCodes(input);
        UserScope scope = userHolder.getUserScope();
        assertCompanyAccess(scope, command.companyId());
        Long assignedUserId = effectiveAssignedUserId(scope);

        String payloadHash = ExchangePayloadHash.calculateByCodes(command, assignedUserId);
        ExchangeBatch existing = batchMapper.selectByRequestId(command.requestId());
        if (existing != null) {
            verifyPayload(existing, payloadHash, assignedUserId);
            CorsOperation operation = operationMapper.selectByBusiness(BIZ_TYPE, existing.getId());
            return new ExchangeReservation(existing.getId(), operation == null ? null : operation.getId(), false);
        }

        CompanyExchangeConfig exchangeConfig = validateCompanyAndExchangeConfig(command.companyId());
        LocalDateTime now = LocalDateTime.now(clock);
        List<ServiceCode> codes = serviceCodeMapper.selectByIdsForExchange(command.serviceCodeIds());
        ExchangeBatch concurrent = batchMapper.selectByRequestIdForUpdate(command.requestId());
        if (concurrent != null) {
            verifyPayload(concurrent, payloadHash, assignedUserId);
            CorsOperation operation = operationMapper.selectByBusiness(BIZ_TYPE, concurrent.getId());
            return new ExchangeReservation(concurrent.getId(), operation == null ? null : operation.getId(), false);
        }
        String specCode = validateExactCodes(codes, command.serviceCodeIds(), command.companyId(), now);
        ServiceDurationConfig spec = findSpec(specCode);

        ExchangeBatch batch = createBatch(command.requestId(), command.companyId(), EXACT_GENERATION_SOURCE,
                specCode, command.serviceCodeIds().size(), spec, userHolder.getOperator(),
                exchangeConfig.getAccountPrefix(), payloadHash, assignedUserId, now);
        batchMapper.insert(batch);
        return persistSelectedCodes(batch, codes, assignedUserId, spec, now, false);
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

    public ExchangeReservation findExistingByCodes(ServiceCodeExchangeByCodesCommand input) {
        ServiceCodeExchangeByCodesCommand command = normalizeByCodes(input);
        UserScope scope = userHolder.getUserScope();
        assertCompanyAccess(scope, command.companyId());
        Long assignedUserId = effectiveAssignedUserId(scope);
        ExchangeBatch batch = batchMapper.selectByRequestId(command.requestId());
        if (batch == null) {
            return null;
        }
        verifyPayload(batch, ExchangePayloadHash.calculateByCodes(command, assignedUserId), assignedUserId);
        CorsOperation operation = operationMapper.selectByBusiness(BIZ_TYPE, batch.getId());
        return new ExchangeReservation(batch.getId(), operation == null ? null : operation.getId(), false);
    }

    private ExchangeReservation persistSelectedCodes(ExchangeBatch batch, List<ServiceCode> codes,
                                                     Long assignedUserId, ServiceDurationConfig spec,
                                                     LocalDateTime now, boolean validateSnapshots) {
        if (validateSnapshots) {
            validateSnapshots(codes, spec);
        }
        List<ExchangeDetail> details = new ArrayList<>(codes.size());
        for (int index = 0; index < codes.size(); index++) {
            ServiceCode code = codes.get(index);
            ExchangeDetail detail = new ExchangeDetail();
            detail.setExchangeBatchId(batch.getId());
            detail.setDetailIndex(index + 1);
            detail.setServiceCodeId(code.getId());
            detail.setActiveServiceCodeId(code.getId());
            detail.setRequestId(batch.getRequestId());
            detail.setServiceCodeSnapshot(serialize(new ExchangeCodeSnapshot(
                    code.getId(), code.getCode(), code.getOwnerCompanyId(), assignedUserId,
                    code.getSpecCode(), code.getServiceType(), code.getDurationDays(),
                    code.getCodeSilenceDays(), code.getExpireAt())));
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
        if (serviceCodeMapper.reserveForExchange(serviceCodeIds, batch.getOwnerCompanyId(),
                batch.getRequestId(), now) != codes.size()) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "服务码预留状态发生并发变化");
        }

        CorsOperation operation = new CorsOperation();
        // The external exchange requestId identifies the Vantix request. This separate
        // value identifies exactly one CORS side effect and is persisted before commit.
        operation.setRequestId("EXCHANGE_" + UUID.randomUUID());
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
        if (requestId.isBlank() || requestId.length() > 128 || hasControl(requestId)
                || specCode.isBlank() || specCode.length() > 32 || hasControl(specCode)
                || input.quantity() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "兑换参数非法");
        }
        return new ServiceCodeExchangeCommand(requestId, input.companyId(), specCode,
                input.generationSource(), input.quantity());
    }

    static ServiceCodeExchangeByCodesCommand normalizeByCodes(ServiceCodeExchangeByCodesCommand input) {
        if (input == null || input.requestId() == null || input.companyId() == null
                || input.companyId() <= 0 || input.serviceCodeIds() == null
                || input.serviceCodeIds().isEmpty() || input.serviceCodeIds().size() > MAX_EXACT_CODES) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "精确兑换参数不完整或数量非法");
        }
        String requestId = input.requestId().trim();
        if (requestId.isBlank() || requestId.length() > 128 || hasControl(requestId)
                || input.serviceCodeIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "精确兑换参数非法");
        }
        Set<Long> distinctIds = new HashSet<>(input.serviceCodeIds());
        if (distinctIds.size() != input.serviceCodeIds().size()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "服务码 ID 列表不能包含重复值");
        }
        return new ServiceCodeExchangeByCodesCommand(requestId, input.companyId(),
                input.serviceCodeIds().stream().sorted().toList());
    }

    private ExchangeBatch createBatch(ServiceCodeExchangeCommand command, ServiceDurationConfig spec,
                                      OperatorIdentity operator,
                                      String accountPrefix, String payloadHash,
                                      Long assignedUserId, LocalDateTime now) {
        return createBatch(command.requestId(), command.companyId(), command.generationSource().name(),
                command.specCode(), command.quantity(), spec, operator, accountPrefix, payloadHash,
                assignedUserId, now);
    }

    private ExchangeBatch createBatch(String requestId, Long companyId, String generationSource,
                                      String specCode, Integer quantity, ServiceDurationConfig spec,
                                      OperatorIdentity operator, String accountPrefix, String payloadHash,
                                      Long assignedUserId, LocalDateTime now) {
        ExchangeBatch batch = new ExchangeBatch();
        batch.setExchangeBatchNo("EX-" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        batch.setRequestId(requestId);
        batch.setOwnerCompanyId(companyId);
        batch.setAssignedUserId(assignedUserId);
        batch.setGenerationSource(generationSource);
        batch.setSpecCode(specCode);
        batch.setDisplayName(spec.getDisplayName());
        batch.setServiceType(spec.getServiceType());
        batch.setDurationDays(spec.getDurationDays());
        batch.setQuantity(quantity);
        batch.setAccountPrefix(accountPrefix);
        batch.setPayloadHash(payloadHash);
        batch.setAccountSilenceDays(spec.getAccountSilenceDays());
        batch.setStatus(ExchangeStatus.PROCESSING);
        batch.setOperatorUserId(operator.userId());
        batch.setOperatorUserName(operator.userName());
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        return batch;
    }

    private ServiceDurationConfig findSpec(String specCode) {
        return durationMapper.selectBySpecCodes(List.of(specCode)).stream()
                .filter(item -> specCode.equals(item.getSpecCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_INVALID,
                        "服务码规格不存在: " + specCode));
    }

    private CompanyExchangeConfig validateCompanyAndExchangeConfig(Long companyId) {
        if (companyMapper.selectCount(Wrappers.<DealerCompany>lambdaQuery()
                .eq(DealerCompany::getCompanyId, companyId)) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公司不存在: " + companyId);
        }
        CompanyExchangeConfig exchangeConfig = exchangeConfigMapper.selectByCompanyId(companyId);
        if (exchangeConfig == null) {
            throw new BusinessException(ErrorCode.EXCHANGE_CONFIG_REQUIRED,
                    "请先配置兑换账号前缀");
        }
        return exchangeConfig;
    }

    private String validateExactCodes(List<ServiceCode> codes, List<Long> requestedIds,
                                      Long companyId, LocalDateTime now) {
        if (codes == null || codes.size() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "指定服务码不存在");
        }
        String specCode = null;
        for (ServiceCode code : codes) {
            if (code.getOwnerCompanyId() == null || !companyId.equals(code.getOwnerCompanyId())) {
                throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED,
                        "指定服务码不属于当前公司");
            }
            if (code.getStatus() != com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus.PENDING) {
                throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_PENDING,
                        "指定服务码不是待兑换状态");
            }
            if (code.getExpireAt() == null || !code.getExpireAt().isAfter(now)) {
                throw new BusinessException(ErrorCode.SERVICE_CODE_EXPIRED,
                        "指定服务码已过期");
            }
            if (code.getSpecCode() == null || code.getSpecCode().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "指定服务码规格为空");
            }
            if (specCode == null) {
                specCode = code.getSpecCode();
            } else if (!specCode.equals(code.getSpecCode())) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "指定服务码必须属于同一 specCode");
            }
        }
        return specCode;
    }

    private void validateSnapshots(List<ServiceCode> codes, ServiceDurationConfig spec) {
        for (ServiceCode code : codes) {
            if (code.getOwnerCompanyId() == null || code.getGenerateBatchId() == null
                    || !spec.getSpecCode().equals(code.getSpecCode())
                    || !spec.getServiceType().equals(code.getServiceType())
                    || !spec.getDurationDays().equals(code.getDurationDays())
                    || code.getCodeSilenceDays() == null || code.getCodeSilenceDays() < 0
                    || code.getExpireAt() == null) {
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
