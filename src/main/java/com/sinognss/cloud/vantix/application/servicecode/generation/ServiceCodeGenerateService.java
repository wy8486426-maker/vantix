package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateBatch;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Compatibility adapter for callers that still issue a one-spec request.
 * External B2B and offline flows use the order-level service directly.
 */
@Service
public class ServiceCodeGenerateService {
    private final ServiceCodeOrderGenerateService orderGenerateService;
    private final ServiceCodeGenerateBatchMapper batchMapper;
    private final ServiceCodeMapper codeMapper;

    public ServiceCodeGenerateService(ServiceCodeOrderGenerateService orderGenerateService,
                                      ServiceCodeGenerateBatchMapper batchMapper,
                                      ServiceCodeMapper codeMapper) {
        this.orderGenerateService = orderGenerateService;
        this.batchMapper = batchMapper;
        this.codeMapper = codeMapper;
    }

    public GenerateServiceCodeResult generate(GenerateServiceCodeCommand command, OperatorIdentity operator) {
        GenerateServiceCodeOrderCommand orderCommand = new GenerateServiceCodeOrderCommand(
                command.generationSource(), command.requestId(), command.orderNo(), command.orderTime(),
                command.companyId(), List.of(new GenerateServiceCodeItemCommand(
                command.specCode(), command.quantity(), command.remark())));
        ServiceCodeGenerateOrderView result = orderGenerateService.generate(orderCommand, operator);
        ServiceCodeGenerateOrderItemView item = result.items().get(0);
        ServiceCodeGenerateBatch batch = batchMapper.selectByBatchNo(item.batchNo());
        if (batch == null) {
            throw new BusinessException(ErrorCode.BATCH_STATUS_INCONSISTENT, "生成批次不存在");
        }
        List<String> codes = codeMapper.selectByGenerateBatchId(batch.getId())
                .stream().map(code -> code.getCode()).toList();
        return new GenerateServiceCodeResult(ServiceCodeBatchView.from(batch), result.idempotent(), codes);
    }

    public static String businessKeyHash(GenerationSource source, Long companyId, String orderNo, String specCode) {
        return GenerationHash.businessKey(source, companyId, orderNo, specCode);
    }
}