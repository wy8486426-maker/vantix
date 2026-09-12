package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.UserHolderBridge;
import com.sinognss.cloud.vantix.common.user.UserScope;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateBatch;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateBatchMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServiceCodeGenerationQueryService {
    private final ServiceCodeGenerateBatchMapper batchMapper;
    private final UserHolderBridge userHolder;

    public ServiceCodeGenerationQueryService(ServiceCodeGenerateBatchMapper batchMapper, UserHolderBridge userHolder) {
        this.batchMapper = batchMapper;
        this.userHolder = userHolder;
    }

    public ServiceCodeBatchView get(String batchNo) {
        ServiceCodeGenerateBatch batch = batchMapper.selectByBatchNo(batchNo);
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务码生成批次不存在: " + batchNo);
        }
        assertAccessible(batch.getOwnerCompanyId());
        return ServiceCodeBatchView.from(batch);
    }

    public List<ServiceCodeBatchView> byOrderNo(String orderNo, Long requestedCompanyId) {
        UserScope scope = userHolder.getUserScope();
        Long companyId = requestedCompanyId;
        if (!scope.isGlobal()) {
            if (requestedCompanyId != null && !scope.canAccessCompany(requestedCompanyId)) {
                throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权查询该公司的生成批次");
            }
            companyId = scope.companyId();
        }
        return batchMapper.selectByOrderNo(orderNo, companyId).stream().map(ServiceCodeBatchView::from).toList();
    }

    private void assertAccessible(Long companyId) {
        if (!userHolder.getUserScope().canAccessCompany(companyId)) {
            throw new BusinessException(ErrorCode.SERVICE_CODE_NOT_OWNED, "无权访问该公司的生成批次");
        }
    }
}