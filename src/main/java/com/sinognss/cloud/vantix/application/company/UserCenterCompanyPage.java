package com.sinognss.cloud.vantix.application.company;

import java.util.List;

public record UserCenterCompanyPage(long currentPage, long totalPage, int sourceItemCount,
                                    List<UserCenterCompany> companies) {
}
