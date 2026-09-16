package com.sinognss.cloud.vantix.integration.usercenter;

import com.sinognss.cloud.base.common.api.CommonResult;
import com.sinognss.cloud.vantix.application.company.UserCenterCompany;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserCenterCompanyFeignAdapterTest {
    private final UserCenterFeignService feignService = mock(UserCenterFeignService.class);
    private final UserCenterCompanyFeignAdapter adapter = new UserCenterCompanyFeignAdapter(feignService);

    @Test
    void preciseLookupMatchesRequestedCompanyIdInsteadOfFirstItem() {
        when(feignService.selectListByCompanyIdList(List.of(100L))).thenReturn(
                CommonResult.success(List.of(new CompanyCommonVO(999L, "other"),
                        new CompanyCommonVO(100L, "target"))));

        Optional<UserCenterCompany> result = adapter.findByCompanyId(100L);

        assertTrue(result.isPresent());
        assertEquals(new UserCenterCompany(100L, "target"), result.get());
    }

    @Test
    void successfulLookupWithoutRequestedCompanyReturnsEmpty() {
        when(feignService.selectListByCompanyIdList(List.of(100L))).thenReturn(
                CommonResult.success(List.of(new CompanyCommonVO(999L, "other"))));

        assertTrue(adapter.findByCompanyId(100L).isEmpty());
    }

    @Test
    void failedCommonResultIsSyncFailure() {
        when(feignService.selectListByCompanyIdList(List.of(100L)))
                .thenReturn(CommonResult.failed("down"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adapter.findByCompanyId(100L));

        assertEquals(ErrorCode.COMPANY_SYNC_FAILED, exception.getVantixErrorCode());
    }

    @Test
    void invalidExactCompanyPayloadIsSyncFailure() {
        when(feignService.selectListByCompanyIdList(List.of(100L))).thenReturn(
                CommonResult.success(List.of(new CompanyCommonVO(100L, "  "))));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adapter.findByCompanyId(100L));

        assertEquals(ErrorCode.COMPANY_SYNC_FAILED, exception.getVantixErrorCode());
    }
}
