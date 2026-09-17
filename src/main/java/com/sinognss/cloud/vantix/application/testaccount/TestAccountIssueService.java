package com.sinognss.cloud.vantix.application.testaccount;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.domain.account.TestAccountIssueBatch;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import com.sinognss.cloud.vantix.infrastructure.mapper.CorsOperationMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.TestAccountIssueBatchMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TestAccountIssueService {
    private static final String DEFAULT_PREFIX = "TEST";
    private final TestAccountIssueBatchMapper batchMapper;
    private final CorsOperationMapper operationMapper;
    private final ServiceDurationConfigMapper durationMapper;
    private final DealerCompanyMapper companyMapper;
    private final UserHolderBridge userHolder;
    private final GenerationProperties generationProperties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final TestAccountIssueProcessor processor;
    private final TestAccountIssueQueryService queryService;

    public TestAccountIssueService(TestAccountIssueBatchMapper batchMapper,
                                   CorsOperationMapper operationMapper,
                                   ServiceDurationConfigMapper durationMapper,
                                   DealerCompanyMapper companyMapper,
                                   UserHolderBridge userHolder,
                                   GenerationProperties generationProperties,
                                   Clock clock,
                                   PlatformTransactionManager transactionManager,
                                   TestAccountIssueProcessor processor,
                                   TestAccountIssueQueryService queryService) {
        this.batchMapper = batchMapper;
        this.operationMapper = operationMapper;
        this.durationMapper = durationMapper;
        this.companyMapper = companyMapper;
        this.userHolder = userHolder;
        this.generationProperties = generationProperties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.processor = processor;
        this.queryService = queryService;
    }

    public TestAccountIssueView issue(TestAccountIssueCommand input) {
        TestAccountIssueCommand command = normalize(input);
        TestAccountIssueReservation reservation = reserve(command);
        if (reservation.operationId() == null) {
            throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT,
                    "测试账号下发批次缺少 CORS 操作记录");
        }
        if (reservation.created()) processor.process(reservation.operationId());
        return queryService.get(command.requestId());
    }

    public TestAccountIssueReservation reserve(TestAccountIssueCommand input) {
        TestAccountIssueCommand command = normalize(input);
        assertGlobal();
        validateQuantity(command);
        String payloadHash = TestAccountIssuePayloadHash.calculate(command);
        try {
            return transactionTemplate.execute(status -> reserveTransaction(command, payloadHash));
        } catch (DuplicateKeyException duplicate) {
            TestAccountIssueBatch existing = batchMapper.selectByRequestId(command.requestId());
            if (existing == null) throw duplicate;
            verifyPayload(existing, payloadHash);
            CorsOperation operation = operationMapper.selectByBusiness(
                    TestAccountIssueConstants.BIZ_TYPE, existing.getId());
            return new TestAccountIssueReservation(existing.getId(),
                    operation == null ? null : operation.getId(), false);
        }
    }

    protected TestAccountIssueReservation reserveTransaction(TestAccountIssueCommand command,
                                                              String payloadHash) {
        TestAccountIssueBatch existing = batchMapper.selectByRequestId(command.requestId());
        if (existing != null) {
            verifyPayload(existing, payloadHash);
            CorsOperation operation = operationMapper.selectByBusiness(
                    TestAccountIssueConstants.BIZ_TYPE, existing.getId());
            return new TestAccountIssueReservation(existing.getId(),
                    operation == null ? null : operation.getId(), false);
        }
        if (companyMapper.selectCount(Wrappers.<DealerCompany>lambdaQuery()
                .eq(DealerCompany::getCompanyId, command.companyId())) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公司不存在: " + command.companyId());
        }
        ServiceDurationConfig spec = durationMapper.selectEnabledBySpecCode(command.specCode());
        validateSpec(spec, command.specCode());
        LocalDateTime now = LocalDateTime.now(clock);
        OperatorIdentity operator = userHolder.getOperator();
        TestAccountIssueBatch batch = new TestAccountIssueBatch();
        batch.setIssueBatchNo("TEST-" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        batch.setRequestId(command.requestId());
        batch.setOwnerCompanyId(command.companyId());
        batch.setSpecCode(command.specCode());
        batch.setServiceType(spec.getServiceType());
        batch.setDurationDays(spec.getDurationDays());
        batch.setAccountSilenceDays(spec.getAccountSilenceDays());
        batch.setQuantity(command.quantity());
        batch.setAccountPrefix(command.accountPrefix());
        batch.setPayloadHash(payloadHash);
        batch.setStatus(TestAccountIssueConstants.PROCESSING);
        batch.setOperatorUserId(operator.userId());
        batch.setOperatorUserName(operator.userName());
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        if (batchMapper.insert(batch) != 1 || batch.getId() == null) {
            throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT,
                    "测试账号下发批次创建失败");
        }

        CorsOperation operation = new CorsOperation();
        operation.setRequestId("TEST_" + UUID.randomUUID());
        operation.setOperationType(TestAccountIssueConstants.OPERATION_TYPE);
        operation.setBizType(TestAccountIssueConstants.BIZ_TYPE);
        operation.setBizId(batch.getId());
        operation.setStatus(TestAccountIssueConstants.PENDING);
        operation.setRetryCount(0);
        operation.setVersion(0L);
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);
        if (operationMapper.insert(operation) != 1 || operation.getId() == null) {
            throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_STATE_INCONSISTENT,
                    "测试账号下发 CORS 操作创建失败");
        }
        return new TestAccountIssueReservation(batch.getId(), operation.getId(), true);
    }

    static TestAccountIssueCommand normalize(TestAccountIssueCommand input) {
        if (input == null || input.requestId() == null || input.companyId() == null
                || input.quantity() == null || input.specCode() == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "测试账号下发参数不完整");
        }
        String requestId = input.requestId().trim();
        String specCode = input.specCode().trim();
        String prefix = input.accountPrefix() == null || input.accountPrefix().trim().isEmpty()
                ? DEFAULT_PREFIX : input.accountPrefix().trim();
        if (requestId.isBlank() || requestId.length() > 128 || hasControl(requestId)
                || input.companyId() <= 0 || input.quantity() <= 0
                || specCode.isBlank() || specCode.length() > 32 || hasControl(specCode)
                || !prefix.matches("^[A-Za-z0-9]{4}$")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "测试账号下发参数非法");
        }
        return new TestAccountIssueCommand(requestId, input.companyId(), input.quantity(), specCode, prefix);
    }

    private void assertGlobal() {
        UserScope scope = userHolder.getUserScope();
        if (scope == null || !scope.isSupported()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_USER_SCOPE, "当前用户数据范围不受支持");
        }
        if (!scope.isGlobal()) {
            throw new BusinessException(ErrorCode.GLOBAL_SCOPE_REQUIRED, "测试账号下发仅允许 GLOBAL 数据范围");
        }
    }

    private void validateQuantity(TestAccountIssueCommand command) {
        if (command.quantity() > generationProperties.getMaxQuantityPerRequest()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "单次测试账号下发数量不能超过 " + generationProperties.getMaxQuantityPerRequest());
        }
    }

    private static void validateSpec(ServiceDurationConfig spec, String specCode) {
        if (spec == null) throw new BusinessException(ErrorCode.NOT_FOUND, "服务规格不存在或未启用: " + specCode);
        if (spec.getDurationDays() == null || spec.getDurationDays() <= 0
                || spec.getAccountSilenceDays() == null || spec.getAccountSilenceDays() < 0
                || spec.getServiceType() == null || spec.getServiceType().isBlank()) {
            throw new BusinessException(ErrorCode.CONFIG_INVALID, "服务规格配置无效: " + specCode);
        }
    }

    private static void verifyPayload(TestAccountIssueBatch existing, String payloadHash) {
        if (!payloadHash.equals(existing.getPayloadHash())) {
            throw new BusinessException(ErrorCode.TEST_ACCOUNT_ISSUE_IDEMPOTENCY_CONFLICT,
                    "requestId 已用于不同的测试账号下发参数");
        }
    }

    private static boolean hasControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
