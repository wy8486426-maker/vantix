package com.sinognss.cloud.vantix.integration.usercenter;

import com.sinognss.cloud.base.common.api.CommonResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "user-center", path = "/usercenter", configuration = UserCenterFeignConfiguration.class)
public interface UserCenterFeignService {
    @RequestMapping(value = "/company/selectListByCompanyIdList", method = RequestMethod.GET)
    CommonResult<List<CompanyCommonVO>> selectListByCompanyIdList(
            @RequestParam("companyIdList") List<Long> companyIdList);

    @RequestMapping(value = "/company/choicePage", method = RequestMethod.GET)
    CommonResult<PageUtil<CompanySelectVO>> choicePage(@RequestParam("pageSize") long pageSize,
                                         @RequestParam("currPage") long currPage);
}
