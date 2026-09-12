package com.sinognss.cloud.vantix.application.offline;

import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeItemCommand;
import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeOrderCommand;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeGenerateOrderView;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeOrderGenerateService;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class OfflineOrderImportTransaction {
    private final ServiceCodeOrderGenerateService generateService;

    public OfflineOrderImportTransaction(ServiceCodeOrderGenerateService generateService) {
        this.generateService = generateService;
    }

    @Transactional
    public List<ServiceCodeGenerateOrderView> generateAll(Long companyId,
                                                           List<ParsedOfflineOrderGroup> groups,
                                                           OperatorIdentity operator) {
        List<ServiceCodeGenerateOrderView> results = new ArrayList<>(groups.size());
        for (ParsedOfflineOrderGroup group : groups) {
            String requestId = ServiceCodeOrderGenerateService.offlineRequestId(companyId, group.orderNo());
            List<GenerateServiceCodeItemCommand> items = group.items().stream()
                    .map(row -> new GenerateServiceCodeItemCommand(row.specCode(), row.quantity(), row.remark()))
                    .toList();
            GenerateServiceCodeOrderCommand command = new GenerateServiceCodeOrderCommand(
                    GenerationSource.OFFLINE, requestId, group.orderNo(), group.orderTime(), companyId, items);
            try {
                results.add(generateService.generate(command, operator));
            } catch (BusinessException exception) {
                String message = exception.getVantixErrorCode()
                        == com.sinognss.cloud.vantix.common.exception.ErrorCode.GENERATION_IDEMPOTENCY_CONFLICT
                        ? "订单内容与历史导入不一致：" + group.orderNo()
                        : exception.getMessage();
                throw new OfflineImportValidationException(List.of(new OfflineImportError(
                        group.rowNumber(), "订单号", message)));
            }
        }
        return List.copyOf(results);
    }
}