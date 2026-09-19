package com.sinognss.cloud.vantix.application.company;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompanyPageViewTest {
    @Test
    void pageViewIncludesManagerFields() {
        CompanyPageRow row = new CompanyPageRow();
        row.setCompanyId(100L);
        row.setCompanyName("company");
        row.setManagerId(123L);
        row.setManagerTel("13800138000");

        CompanyPageView view = CompanyPageView.from(row);

        assertEquals(123L, view.managerId());
        assertEquals("13800138000", view.managerTel());
    }
}
