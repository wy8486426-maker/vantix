package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.sinognss.cloud.vantix.common.user.OperatorIdentity;
import com.sinognss.cloud.vantix.config.GenerationProperties;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import com.sinognss.cloud.vantix.domain.company.DealerCompany;
import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeGenerateOrder;
import com.sinognss.cloud.vantix.infrastructure.mapper.DealerCompanyMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateBatchMapper;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceCodeGenerateOrderMapper;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ServiceCodeOrderGenerateService {
    private final ServiceCodeGenerateOrderMapper orderMapper;
    private final ServiceCodeGenerateBatchMapper batchMapper;
    private final ServiceDurationConfigMapper durationMapper;
    private final DealerCompanyMapper companyMapper;
    private final ServiceCodeBatchGenerateService batchGenerateService;
    private final GenerationProperties properties;
    private final Clock clock;

    public ServiceCodeOrderGenerateService(ServiceCodeGenerateOrderMapper orderMapper,
                                           ServiceCodeGenerateBatchMapper batchMapper,
                                           ServiceDurationConfigMapper durationMapper,
                                           DealerCompanyMapper companyMapper,
                                           ServiceCodeBatchGenerateService batchGenerateService,
                                           GenerationProperties properties,
                                           Clock clock) {
        this.orderMapper = orderMapper;
        this.batchMapper = batchMapper;
        this.durationMapper = durationMapper;
        this.companyMapper = companyMapper;
        this.batchGenerateService = batchGenerateService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ServiceCodeGenerateOrderView generate(GenerateServiceCodeOrderCommand input,
                                                  OperatorIdentity operator) {
        GenerateServiceCodeOrderCommand command = normalizeAndValidate(input, operator);
        String payloadHash = payloadHash(command);

        ServiceCodeGenerateOrder byRequest = orderMapper.selectByRequestId(command.requestId());
        if (byRequest != null) {
            return existing(byRequest, payloadHash, command.generationSource());
        }
        ServiceCodeGenerateOrder byBusiness = orderMapper.selectByBusinessKey(
                command.generationSource().name(), command.companyId(), command.orderNo());
        if (byBusiness != null) {
            return existing(byBusiness, payloadHash, command.generationSource());
        }

        validateCompany(command.companyId());
        Map<String, ServiceDurationConfig> specs = validateAndLoadSpecs(command.items());
        int totalQuantity = command.items().stream().mapToInt(GenerateServiceCodeItemCommand::quantity).sum();
        LocalDateTime now = LocalDateTime.now(clock);
        ServiceCodeGenerateOrder order = new ServiceCodeGenerateOrder();
        order.setRequestId(command.requestId());
        order.setGenerationSource(command.generationSource());
        order.setSourceOrderNo(command.orderNo());
        order.setSourceOrderTime(command.orderTime());
        order.setOwnerCompanyId(command.companyId());
        order.setPayloadHash(payloadHash);
        order.setItemCount(command.items().size());
        order.setTotalQuantity(totalQuantity);
        order.setStatus("GENERATING");
        order.setOperatorUserId(operator.userId());
        order.setOperatorUserName(operator.userName());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException exception) {
            ServiceCodeGenerateOrder concurrent = orderMapper.selectByRequestIdForUpdate(command.requestId());
            if (concurrent == null) {
                concurrent = orderMapper.selectByBusinessKeyForUpdate(
                        command.generationSource().name(), command.companyId(), command.orderNo());
            }
            if (concurrent != null) {
                return existing(concurrent, payloadHash, command.generationSource());
            }
            throw new BusinessException(ErrorCode.GENERATION_CONCURRENT_RETRY,
                    "订单正在并发生成，请使用同一 requestId 重试");
        }

        Set<String> generatedCodes = new HashSet<>();
        for (GenerateServiceCodeItemCommand item : command.items()) {
            batchGenerateService.generateBatch(order.getId(), command, item,
                    specs.get(item.specCode()), operator, generatedCodes);
        }
        if (orderMapper.complete(order.getId(), LocalDateTime.now(clock)) != 1) {
            throw new BusinessException(ErrorCode.BATCH_STATUS_INCONSISTENT, "生成订单状态更新失败");
        }
        order.setStatus("COMPLETED");
        return view(order, false);
    }

    private GenerateServiceCodeOrderCommand normalizeAndValidate(GenerateServiceCodeOrderCommand command,
                                                                  OperatorIdentity operator) {
        if (command == null || command.generationSource() == null || command.requestId() == null
                || command.requestId().isBlank() || command.requestId().trim().length() > 160
                || command.requestId().codePoints().anyMatch(Character::isISOControl)
                || command.orderNo() == null || command.orderNo().isBlank()
                || command.orderNo().trim().length() > 128
                || command.orderNo().codePoints().anyMatch(Character::isISOControl)
                || command.companyId() == null || command.companyId() <= 0
                || command.items() == null || command.items().isEmpty()
                || command.items().size() > properties.getMaxItemsPerOrder()
                || operator == null || (operator.userName() != null && operator.userName().length() > 128)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "服务码生成订单参数非法");
        }
        String requestId = command.requestId().trim();
        String orderNo = command.orderNo().trim();
        List<GenerateServiceCodeItemCommand> items = new ArrayList<>(command.items().size());
        Set<String> codes = new HashSet<>();
        long totalQuantity = 0;
        for (GenerateServiceCodeItemCommand item : command.items()) {
            if (item == null || item.specCode() == null || item.specCode().isBlank()
                    || item.specCode().trim().length() > 32 || item.quantity() == null
                    || item.quantity() <= 0 || item.quantity() > properties.getMaxQuantityPerRequest()
                    || (item.remark() != null && item.remark().length() > 512)) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "服务码规格参数非法");
            }
            String specCode = item.specCode().trim();
            if (!codes.add(specCode)) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "订单内存在重复服务码规格: " + specCode);
            }
            totalQuantity += item.quantity();
            if (totalQuantity > properties.getMaxTotalQuantityPerOrder()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "订单服务码总量不能超过 " + properties.getMaxTotalQuantityPerOrder());
            }
            items.add(new GenerateServiceCodeItemCommand(specCode, item.quantity(), item.remark()));
        }
        items.sort(Comparator.comparing(GenerateServiceCodeItemCommand::specCode));
        return new GenerateServiceCodeOrderCommand(command.generationSource(), requestId, orderNo,
                command.orderTime(), command.companyId(), List.copyOf(items));
    }

    private Map<String, ServiceDurationConfig> validateAndLoadSpecs(List<GenerateServiceCodeItemCommand> items) {
        List<String> specCodes = items.stream().map(GenerateServiceCodeItemCommand::specCode).toList();
        Map<String, ServiceDurationConfig> result = new HashMap<>();
        for (ServiceDurationConfig spec : durationMapper.selectBySpecCodes(specCodes)) {
            if (Boolean.TRUE.equals(spec.getEnabled())) {
                result.put(spec.getSpecCode(), spec);
            }
        }
        for (String specCode : specCodes) {
            if (!result.containsKey(specCode)) {
                throw new BusinessException(ErrorCode.CONFIG_INVALID,
                        "服务码规格不存在或已停用: " + specCode);
            }
        }
        return result;
    }

    private void validateCompany(Long companyId) {
        if (companyMapper.selectCount(Wrappers.<DealerCompany>lambdaQuery()
                .eq(DealerCompany::getCompanyId, companyId)) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公司不存在: " + companyId);
        }
    }

    private ServiceCodeGenerateOrderView existing(ServiceCodeGenerateOrder order, String payloadHash,
                                                   GenerationSource source) {
        if (!payloadHash.equals(order.getPayloadHash())) {
            throw new BusinessException(ErrorCode.GENERATION_IDEMPOTENCY_CONFLICT,
                    "requestId 或订单号已用于不同的服务码生成内容");
        }
        if (source == GenerationSource.OFFLINE) {
            throw new BusinessException(ErrorCode.GENERATION_ALREADY_EXISTS,
                    "该订单已经导入: " + order.getSourceOrderNo());
        }
        return view(order, true);
    }

    private ServiceCodeGenerateOrderView view(ServiceCodeGenerateOrder order, boolean idempotent) {
        List<ServiceCodeGenerateOrderItemView> items = batchMapper.selectByGenerateOrderId(order.getId())
                .stream().map(ServiceCodeBatchView::from)
                .map(ServiceCodeGenerateOrderItemView::from).toList();
        return new ServiceCodeGenerateOrderView(order.getRequestId(), order.getGenerationSource().name(),
                order.getSourceOrderNo(), order.getOwnerCompanyId(), order.getSourceOrderTime(), order.getStatus(),
                order.getItemCount(), order.getTotalQuantity(), items, idempotent, order.getCreatedAt());
    }

    public static String offlineRequestId(Long companyId, String orderNo) {
        return "OFFLINE:" + companyId + ":" + GenerationHash.sha256(orderNo.trim());
    }

    public static String payloadHash(GenerateServiceCodeOrderCommand command) {
        List<GenerateServiceCodeItemCommand> items = command.items().stream()
                .sorted(Comparator.comparing(item -> item.specCode().trim())).toList();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateField(digest, command.generationSource().name());
            updateField(digest, command.companyId().toString());
            updateField(digest, command.orderNo().trim());
            updateField(digest, command.orderTime() == null ? null : command.orderTime().toString());
            updateField(digest, Integer.toString(items.size()));
            for (GenerateServiceCodeItemCommand item : items) {
                updateField(digest, item.specCode().trim());
                updateField(digest, item.quantity().toString());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateField(MessageDigest digest, String value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
