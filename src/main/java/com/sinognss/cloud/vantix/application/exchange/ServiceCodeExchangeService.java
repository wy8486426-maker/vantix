package com.sinognss.cloud.vantix.application.exchange;

import com.sinognss.cloud.vantix.application.cors.CorsOperationProcessor;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ServiceCodeExchangeService {
    private final ServiceCodeExchangeReserveService reserveService;
    private final CorsOperationProcessor processor;
    private final ExchangeQueryService queryService;

    public ServiceCodeExchangeService(ServiceCodeExchangeReserveService reserveService,
                                      CorsOperationProcessor processor,
                                      ExchangeQueryService queryService) {
        this.reserveService = reserveService;
        this.processor = processor;
        this.queryService = queryService;
    }

    public ServiceCodeExchangeView exchange(ServiceCodeExchangeCommand command) {
        ExchangeReservation reservation;
        try {
            reservation = reserveService.reserve(command);
        } catch (DuplicateKeyException duplicate) {
            reservation = reserveService.findExisting(command);
            if (reservation == null) throw duplicate;
        }
        return processReservation(reservation, ServiceCodeExchangeReserveService.normalize(command).requestId());
    }

    public ServiceCodeExchangeView exchangeByCodes(ServiceCodeExchangeByCodesCommand command) {
        ExchangeReservation reservation;
        try {
            reservation = reserveService.reserveByCodes(command);
        } catch (DuplicateKeyException duplicate) {
            reservation = reserveService.findExistingByCodes(command);
            if (reservation == null) throw duplicate;
        }
        return processReservation(reservation,
                ServiceCodeExchangeReserveService.normalizeByCodes(command).requestId());
    }

    private ServiceCodeExchangeView processReservation(ExchangeReservation reservation, String requestId) {
        if (reservation.operationId() == null) {
            throw new BusinessException(ErrorCode.EXCHANGE_STATE_INCONSISTENT,
                    "兑换批次缺少 CORS 操作记录");
        }
        if (reservation.created()) {
            processor.process(reservation.operationId());
        }
        return queryService.get(requestId);
    }
}
