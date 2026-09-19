package com.sinognss.cloud.vantix.integration.usercenter;

import com.sinognss.cloud.base.common.api.CommonResult;
import com.sinognss.cloud.vantix.application.company.UserCenterCompany;
import com.sinognss.cloud.vantix.application.company.UserCenterCompanyPage;
import com.sinognss.cloud.vantix.common.exception.BusinessException;
import com.sinognss.cloud.vantix.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                CommonResult.success(List.of(new CompanyCommonVO(999L, "other", 999L, "13800000000"),
                        new CompanyCommonVO(100L, "target", 123L, "13800138000"))));

        Optional<UserCenterCompany> result = adapter.findByCompanyId(100L);

        assertTrue(result.isPresent());
        assertEquals(new UserCenterCompany(100L, "target", 123L, "13800138000"), result.get());
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
    void companyDtosDeserializeManagerFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CompanyCommonVO common = mapper.readValue(
                "{\"id\":100,\"name\":\"target\",\"managerId\":123,\"managerTel\":\"13800138000\"}",
                CompanyCommonVO.class);
        CompanySelectVO select = mapper.readValue(
                "{\"id\":100,\"name\":\"target\",\"managerId\":123,\"managerTel\":\"13800138000\"}",
                CompanySelectVO.class);

        assertEquals(123L, common.managerId());
        assertEquals("13800138000", common.managerTel());
        assertEquals(123L, select.managerId());
        assertEquals("13800138000", select.managerTel());
    }

    @Test
    void pageMapsManagerFieldsIntoSyncCompany() {
        when(feignService.choicePage(200, 1)).thenReturn(CommonResult.success(
                new PageUtil<>(1L, 200L, 1L, 1L,
                        List.of(new CompanySelectVO(100L, "target", 123L, "13800138000")))));

        UserCenterCompanyPage result = adapter.page(1, 200);

        assertEquals(new UserCenterCompany(100L, "target", 123L, "13800138000"),
                result.companies().get(0));
    }

    @Test
    void invalidExactCompanyPayloadIsSyncFailure() {
        when(feignService.selectListByCompanyIdList(List.of(100L))).thenReturn(
                CommonResult.success(List.of(new CompanyCommonVO(100L, "  "))));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adapter.findByCompanyId(100L));

        assertEquals(ErrorCode.COMPANY_SYNC_FAILED, exception.getVantixErrorCode());
    }

//    @Test
//    void pageRetainsSourceItemCountWhenAllItemsAreInvalid() {
//        when(feignService.choicePage(200, 1)).thenReturn(new PageUtil<>(2L, 200L, 2L, 1L,
//                List.of(new CompanySelectVO(null, "invalid"), new CompanySelectVO(0L, "also invalid"))));
//
//        UserCenterCompanyPage result = adapter.page(1, 200);
//
//        assertEquals(2, result.sourceItemCount());
//        assertTrue(result.companies().isEmpty());
//    }
}
