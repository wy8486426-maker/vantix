package com.sinognss.cloud.vantix.application.company;

import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DealerCompanySyncSchedulerTest {
    private final DealerCompanySyncService syncService = mock(DealerCompanySyncService.class);
    private final DealerCompanySyncScheduler scheduler = new DealerCompanySyncScheduler(syncService);

    @Test
    void remoteFailureIsContainedUntilNextScheduledRun() {
        when(syncService.syncAllCompanies()).thenThrow(
                new BusinessException(ErrorCode.COMPANY_SYNC_FAILED, "unavailable"));

        assertDoesNotThrow(scheduler::sync);
        verify(syncService).syncAllCompanies();
    }
}
