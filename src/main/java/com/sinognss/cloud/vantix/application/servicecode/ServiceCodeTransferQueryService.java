package com.sinognss.cloud.vantix.application.servicecode;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeTransferQueryMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ServiceCodeTransferQueryService {
    private final ServiceCodeTransferQueryMapper mapper;
    private final UserHolderBridge userHolder;

    public ServiceCodeTransferQueryService(ServiceCodeTransferQueryMapper mapper, UserHolderBridge userHolder) {
        this.mapper = mapper;
        this.userHolder = userHolder;
    }

    public PageResponse<TransferBatchView> page(ServiceCodeTransferPageQuery input) {
        ServiceCodeTransferPageQuery query = validate(input);
        Long scopeCompanyId = scopeCompanyId();
        IPage<TransferBatchQueryRow> page = mapper.pageForFrontend(new Page<>(query.current(), query.size()),
                query.keyword(), name(query.transferType()), query.fromCompanyId(), query.toCompanyId(),
                query.createdFrom(), query.createdTo(), query.specCode(), query.durationDays(), scopeCompanyId);
        return new PageResponse<>(page.getRecords().stream().map(TransferBatchView::from).toList(),
                page.getCurrent(), page.getSize(), page.getTotal(), page.getPages());
    }

    public TransferDetailView detail(String inputTransferNo) {
        String transferNo = normalize(inputTransferNo, 64, "transferNo");
        TransferBatchQueryRow batch = mapper.detailBatch(transferNo, scopeCompanyId());
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "转赠批次不存在: " + transferNo);
        }
        return new TransferDetailView(TransferBatchView.from(batch),
                mapper.detailItems(transferNo).stream().map(TransferItemView::from).toList());
    }

    private Long scopeCompanyId() {
        UserScope scope = userHolder.getUserScope();
        if (!scope.isSupported()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_USER_SCOPE, "当前用户数据范围不受支持");
        }
        return scope.isGlobal() ? null : scope.companyId();
    }

    private ServiceCodeTransferPageQuery validate(ServiceCodeTransferPageQuery input) {
        if (input == null || input.current() < 1 || input.size() < 1 || input.size() > 500) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "分页参数非法");
        }
        if ((input.fromCompanyId() != null && input.fromCompanyId() <= 0)
                || (input.toCompanyId() != null && input.toCompanyId() <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "公司 ID 必须大于 0");
        }
        if (input.durationDays() != null && input.durationDays() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "durationDays 必须大于 0");
        }
        if (input.createdFrom() != null && input.createdTo() != null
                && input.createdFrom().isAfter(input.createdTo())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "createdFrom 不能晚于 createdTo");
        }
        return new ServiceCodeTransferPageQuery(input.current(), input.size(), normalize(input.keyword(), 100, "keyword"),
                input.transferType(), input.fromCompanyId(), input.toCompanyId(), input.createdFrom(), input.createdTo(),
                normalize(input.specCode(), 32, "specCode"), input.durationDays());
    }

    private String normalize(String value, int maxLength, String name) {
        if (value == null) return null;
        String result = value.trim();
        if (result.isEmpty()) return null;
        if (result.length() > maxLength || result.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, name + " 参数非法");
        }
        return result;
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
