package com.sinognss.cloud.vantix.application.company;

import java.util.List;

public record UserCenterCompanyPage(long currentPage, long totalPage,
                                    List<UserCenterCompany> companies) {
}
