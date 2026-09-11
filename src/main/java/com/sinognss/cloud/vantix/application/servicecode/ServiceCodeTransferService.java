package com.sinognss.cloud.vantix.application.servicecode;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sinognss.cloud.vantix.application.company.CompanyService;
import com.sinognss.cloud.vantix.application.config.SystemCompanyResolver;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCode;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeTransfer;
import com.sinognss.cloud.vantix.domain.servicecode.TransferType;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeTransferMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ServiceCodeTransferService {
    private static final Logger log = LoggerFactory.getLogger(ServiceCodeTransferService.class);

    private final ServiceCodeMapper serviceCodeMapper;
    private final ServiceCodeTransferMapper transferMapper;
    private final DealerCompanyMapper companyMapper;
    private final CompanyService companyService;
    private final SystemCompanyResolver systemCompanyResolver;
    private final UserHolderBridge userHolder;
    private final Clock clock;

    public ServiceCodeTransferService(ServiceCodeMapper serviceCodeMapper,
                                      ServiceCodeTransferMapper transferMapper,
                                      DealerCompanyMapper companyMapper,
                                      CompanyService companyService,
                                      SystemCompanyResolver systemCompanyResolver,
                                      UserHolderBridge userHolder,
                                      Clock clock) {
        this.serviceCodeMapper = serviceCodeMapper;
        this.transferMapper = transferMapper;
        this.companyMapper = companyMapper;
        this.companyService = companyService;
        this.systemCompanyResolver = systemCompanyResolver;
        this.userHolder = userHolder;
        this.clock = clock;
    }

    @Transactional
    public TransferResult transfer(TransferServiceCodeCommand command) {
        validateCommand(command);
        Long from = command.fromCompanyId();
        Long to = command.toCompanyId();
        if (from.equals(to)) {
            throw new BusinessException(ErrorCode.TRANSFER_NOT_ALLOWED, "转出和转入公司不能相同");
        }
        Long systemCompanyId = systemCompanyResolver.requireId();
        validateCompaniesAndRelation(from, to, systemCompanyId);
        if (!userHolder.getUserScope().canAccessCompany(from)) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "当前用户无权从该公司转出服务码");
        }

        List<Long> ids = command.serviceCodeIds().stream().distinct().sorted().toList();
        if (ids.size() != command.serviceCodeIds().size()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "服务码列表不能包含重复 ID");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<ServiceCode> lockedCodes = serviceCodeMapper.selectList(Wrappers.<ServiceCode>query()
                .in("id", ids)
                .orderByAsc("id")
                .last("FOR UPDATE"));
        if (lockedCodes.size() != ids.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "批量转赠中存在不存在的服务码");
        }
        for (ServiceCode code : lockedCodes) {
            validateTransferable(code, from, now);
        }

        OperatorIdentity operator = userHolder.getOperator();
        TransferType transferType = transferType(from, to, systemCompanyId);
        String transferNo = "TR" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        for (ServiceCode code : lockedCodes) {
            int affectedRows = serviceCodeMapper.transferWithCas(code.getId(), from, to, code.getVersion(), now, now);
            if (affectedRows != 1) {
                throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "服务码状态已变化，请重试");
            }
            ServiceCodeTransfer transfer = new ServiceCodeTransfer();
            transfer.setTransferNo(transferNo);
            transfer.setServiceCodeId(code.getId());
            transfer.setServiceCode(code.getCode());
            transfer.setFromCompanyId(from);
            transfer.setToCompanyId(to);
            transfer.setTransferType(transferType);
            transfer.setOperatorUserId(operator.userId());
            transfer.setOperatorUserName(operator.userName());
            transfer.setCreatedAt(now);
            transferMapper.insert(transfer);
        }
        log.info("Service code batch transferred, transferNo={}, fromCompanyId={}, toCompanyId={}, count={}, operatorUserId={}",
                transferNo, from, to, lockedCodes.size(), operator.userId());
        return new TransferResult(transferNo, lockedCodes.size());
    }

    public List<TransferView> history(Long serviceCodeId) {
        ServiceCode code = serviceCodeServiceRequired(serviceCodeId);
        if (!userHolder.getUserScope().canAccessCompany(code.getOwnerCompanyId())) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权访问该服务码转赠历史");
        }
        return transferMapper.selectList(Wrappers.<ServiceCodeTransfer>lambdaQuery()
                        .eq(ServiceCodeTransfer::getServiceCodeId, serviceCodeId)
                        .orderByDesc(ServiceCodeTransfer::getCreatedAt))
                .stream().map(TransferView::from).toList();
    }

    private void validateCompaniesAndRelation(Long from, Long to, Long systemCompanyId) {
        if (!from.equals(systemCompanyId)) {
            companyService.getRequired(from);
        }
        if (!to.equals(systemCompanyId)) {
            companyService.getRequired(to);
        }
        if (from.equals(systemCompanyId) || to.equals(systemCompanyId)) {
            return;
        }
        DealerCompany fromCompany = companyService.getRequired(from);
        DealerCompany toCompany = companyService.getRequired(to);
        boolean directRelation = from.equals(toCompany.getParentCompanyId())
                || to.equals(fromCompany.getParentCompanyId());
        if (!directRelation) {
            throw new BusinessException(ErrorCode.TRANSFER_NOT_ALLOWED, "双方没有直接上下级关系");
        }
    }

    private TransferType transferType(Long from, Long to, Long systemCompanyId) {
        if (to.equals(systemCompanyId)) {
            return TransferType.TO_SYSTEM;
        }
        if (from.equals(systemCompanyId)) {
            return TransferType.FROM_SYSTEM;
        }
        return TransferType.PARENT_CHILD;
    }

    private void validateTransferable(ServiceCode code, Long fromCompanyId, LocalDateTime now) {
        if (!fromCompanyId.equals(code.getOwnerCompanyId())) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "服务码不属于当前转出公司: " + code.getCode());
        }
        if (code.getStatus() != ServiceCodeStatus.PENDING) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_PENDING, "服务码不是待兑换状态: " + code.getCode());
        }
        if (code.getExpireAt() == null || !code.getExpireAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_EXPIRED, "服务码已过期: " + code.getCode());
        }
    }

    private ServiceCode serviceCodeServiceRequired(Long id) {
        ServiceCode code = id == null ? null : serviceCodeMapper.selectById(id);
        if (code == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务码不存在: " + id);
        }
        return code;
    }

    private void validateCommand(TransferServiceCodeCommand command) {
        if (command == null || command.fromCompanyId() == null || command.toCompanyId() == null
                || command.serviceCodeIds() == null || command.serviceCodeIds().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "批量转赠参数非法");
        }
        if (command.serviceCodeIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "服务码 ID 非法");
        }
    }
}
