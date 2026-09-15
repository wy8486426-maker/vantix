package com.sinognss.cloud.vantix.integration.cors.account;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

public final class CorsUserInfoRepository {
    public static final int MAX_BATCH_SIZE = 1000;
    public static final String STATUS_COLUMNS = "ID, name, active_status, account_status, active_time, "
            + "expiredate, lastupdatetime";
    public static final String FIND_BY_ID_SQL = "SELECT " + STATUS_COLUMNS
            + " FROM userinfo WHERE ID = :id";
    public static final String FIND_BY_IDS_SQL = "SELECT " + STATUS_COLUMNS
            + " FROM userinfo WHERE ID IN (:ids)";

    private final CorsReadOnlyDatabaseClient database;

    public CorsUserInfoRepository(CorsReadOnlyDatabaseClient database) {
        this.database = database;
    }

    public CorsUserInfoStatusRow findById(long id) {
        if (id <= 0) throw new IllegalArgumentException("CORS account ID must be positive");
        List<CorsUserInfoStatusRow> rows = database.jdbc().query(FIND_BY_ID_SQL,
                new MapSqlParameterSource("id", id), CorsUserInfoRepository::mapRow);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<CorsUserInfoStatusRow> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > MAX_BATCH_SIZE) throw new IllegalArgumentException("CORS account batch is too large");
        return database.jdbc().query(FIND_BY_IDS_SQL,
                new MapSqlParameterSource("ids", ids), CorsUserInfoRepository::mapRow);
    }

    private static CorsUserInfoStatusRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new CorsUserInfoStatusRow(rs.getLong("ID"), rs.getString("name"),
                nullableInt(rs, "active_status"), nullableInt(rs, "account_status"),
                localDateTime(rs, "active_time"), localDateTime(rs, "expiredate"),
                localDateTime(rs, "lastupdatetime"));
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static java.time.LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
