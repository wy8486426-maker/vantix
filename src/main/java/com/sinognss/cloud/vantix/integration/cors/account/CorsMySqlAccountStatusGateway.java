package com.sinognss.cloud.vantix.integration.cors.account;

public final class CorsMySqlAccountStatusGateway implements CorsAccountStatusGateway {
    private final CorsUserInfoRepository repository;
    private final CorsUserInfoSnapshotMapper snapshotMapper;

    public CorsMySqlAccountStatusGateway(CorsUserInfoRepository repository,
                                         CorsUserInfoSnapshotMapper snapshotMapper) {
        this.repository = repository;
        this.snapshotMapper = snapshotMapper;
    }

    @Override
    public CorsAccountStatusResult getAccount(String accountId) {
        long id;
        try {
            if (accountId == null || !accountId.matches("[0-9]+")) {
                return CorsAccountStatusResult.unknown("INVALID_LOCAL_ID", "invalid account id");
            }
            id = Long.parseLong(accountId);
            if (id <= 0) return CorsAccountStatusResult.unknown("INVALID_LOCAL_ID", "invalid account id");
        } catch (RuntimeException invalidId) {
            return CorsAccountStatusResult.unknown("INVALID_LOCAL_ID", "invalid account id");
        }
        try {
            CorsUserInfoStatusRow row = repository.findById(id);
            if (row == null) return CorsAccountStatusResult.notFound("CORS_ACCOUNT_NOT_FOUND", "not found");
            return CorsAccountStatusResult.success(snapshotMapper.map(row));
        } catch (IllegalArgumentException invalidRemoteState) {
            return CorsAccountStatusResult.unknown("INVALID_REMOTE_STATE", "invalid remote state");
        } catch (RuntimeException databaseFailure) {
            return CorsAccountStatusResult.unknown("CORS_DB_UNAVAILABLE", "CORS database is unavailable");
        }
    }
}
