package com.sinognss.cloud.vantix.infrastructure.company;

import java.util.Collection;
import java.util.List;

/**
 * Company-service boundary. The remote URL and protocol are intentionally not guessed;
 * an implementation can be supplied once the company service contract is available.
 */
public interface CompanyClient {
    List<CompanyBasicData> getCompanies(Collection<Long> companyIds);
}
