package com.sinognss.cloud.vantix.application.company;

import java.util.Optional;

public interface UserCenterCompanyGateway {
    Optional<UserCenterCompany> findByCompanyId(Long companyId);

    UserCenterCompanyPage page(long currPage, long pageSize);
}
