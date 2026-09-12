package com.sinognss.cloud.vantix.application.offline;

import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeCommand;
import com.sinognss.cloud.vantix.application.servicecode.generation.GenerateServiceCodeResult;
import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeGenerateService;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class OfflineOrderImportTransaction {
    private final ServiceCodeGenerateService generateService;

    public OfflineOrderImportTransaction(ServiceCodeGenerateService generateService) {
        this.generateService = generateService;
    }

    @Transactional
    public List<GenerateServiceCodeResult> generateAll(Long companyId,
                                                       List<ParsedOfflineOrder> orders,
                                                       OperatorIdentity operator) {
        List<GenerateServiceCodeResult> results = new ArrayList<>(orders.size());
        for (ParsedOfflineOrder order : orders) {
            String requestId = "OFFLINE:" + companyId + ":"
                    + ServiceCodeGenerateService.businessKeyHash(
                    GenerationSource.OFFLINE, companyId, order.orderNo(), order.specCode());
            GenerateServiceCodeCommand command = new GenerateServiceCodeCommand(
                    GenerationSource.OFFLINE, requestId, order.orderNo(), order.orderTime(),
                    companyId, order.specCode(), order.quantity(), order.remark());
            results.add(generateService.generate(command, operator));
        }
        return List.copyOf(results);
    }
}