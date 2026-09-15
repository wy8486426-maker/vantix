package com.sinognss.cloud.vantix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "vantix.cors-db")
public class CorsDbProperties {
    private boolean enabled;
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName = "com.mysql.cj.jdbc.Driver";
    private int maximumPoolSize = 5;
    private int minimumIdle;
    private StatusSync statusSync = new StatusSync();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDriverClassName() { return driverClassName; }
    public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
    public int getMaximumPoolSize() { return maximumPoolSize; }
    public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }
    public int getMinimumIdle() { return minimumIdle; }
    public void setMinimumIdle(int minimumIdle) { this.minimumIdle = minimumIdle; }
    public StatusSync getStatusSync() { return statusSync; }
    public void setStatusSync(StatusSync statusSync) { this.statusSync = statusSync; }

    public void validate() {
        if (maximumPoolSize < 1) {
            throw new IllegalArgumentException("maximumPoolSize must be positive");
        }
        if (minimumIdle < 0 || minimumIdle > maximumPoolSize) {
            throw new IllegalArgumentException("minimumIdle must be between 0 and maximumPoolSize");
        }
        if (statusSync == null || statusSync.getBatchSize() < 1 || statusSync.getBatchSize() > 1000) {
            throw new IllegalArgumentException("statusSync.batchSize must be between 1 and 1000");
        }
    }

    public static class StatusSync {
        private boolean enabled;
        private String cron = "0 0 2 * * ?";
        private String zone = "Asia/Shanghai";
        private int batchSize = 500;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
        public String getZone() { return zone; }
        public void setZone(String zone) { this.zone = zone; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }
}
