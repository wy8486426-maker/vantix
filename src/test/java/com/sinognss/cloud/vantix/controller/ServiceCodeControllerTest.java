package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.servicecode.DisplayStatus;
import com.sinognss.cloud.vantix.application.servicecode.PageResponse;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodePageQuery;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeService;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeStatistics;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeStatisticsQuery;
import com.sinognss.cloud.vantix.application.servicecode.ServiceCodeTransferService;
import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceCodeControllerTest {
    private final ServiceCodeService service = mock(ServiceCodeService.class);
    private final ServiceCodeTransferService transferService = mock(ServiceCodeTransferService.class);
    private final ServiceCodeController controller = new ServiceCodeController(service, transferService);

    @Test
    void pageAcceptsFrontendFilters() {
        when(service.page(any(ServiceCodePageQuery.class)))
                .thenReturn(new PageResponse<>(java.util.List.of(), 1, 20, 0, 0));

        controller.page(2, 50, " code ", ServiceCodeStatus.PENDING, DisplayStatus.WAITING,
                "SC001", 90, "ORDER-1", 10L);

        ArgumentCaptor<ServiceCodePageQuery> captor = ArgumentCaptor.forClass(ServiceCodePageQuery.class);
        verify(service).page(captor.capture());
        ServiceCodePageQuery query = captor.getValue();
        assertEquals(2, query.current());
        assertEquals(50, query.size());
        assertEquals(" code ", query.keyword());
        assertEquals("SC001", query.specCode());
        assertEquals(90, query.durationDays());
        assertEquals("ORDER-1", query.sourceOrderNo());
        assertEquals(10L, query.ownerCompanyId());
    }

    @Test
    void statisticsHasOnlyNonStatusFilters() {
        when(service.statistics(any(ServiceCodeStatisticsQuery.class)))
                .thenReturn(new ServiceCodeStatistics(1, 1, 0, 0, 0, 0));

        controller.statistics("keyword", "SC001", 90, "ORDER-1", 10L);

        ArgumentCaptor<ServiceCodeStatisticsQuery> captor = ArgumentCaptor.forClass(ServiceCodeStatisticsQuery.class);
        verify(service).statistics(captor.capture());
        assertEquals("keyword", captor.getValue().keyword());
        assertEquals("SC001", captor.getValue().specCode());
        assertEquals(90, captor.getValue().durationDays());
        assertEquals("ORDER-1", captor.getValue().sourceOrderNo());
        assertEquals(10L, captor.getValue().ownerCompanyId());
    }
}
