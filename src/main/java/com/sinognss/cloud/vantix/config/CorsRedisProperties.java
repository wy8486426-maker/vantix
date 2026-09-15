package com.sinognss.cloud.vantix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "vantix.cors-redis")
public class CorsRedisProperties {
    private boolean enabled;
    private String host;
    private int port = 6379;
    private String username;
    private String password;
    private int database;
    private boolean ssl;
    private String channel = "LW_WEBCORS_PLATUPDATE";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration recoveryInterval = Duration.ofSeconds(5);
    private int workerCoreSize = 2;
    private int workerMaxSize = 4;
    private int queueCapacity = 1000;
    private Duration confirmationDelay = Duration.ofMillis(500);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getDatabase() { return database; }
    public void setDatabase(int database) { this.database = database; }
    public boolean isSsl() { return ssl; }
    public void setSsl(boolean ssl) { this.ssl = ssl; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration value) { this.connectTimeout = value; }
    public Duration getRecoveryInterval() { return recoveryInterval; }
    public void setRecoveryInterval(Duration value) { this.recoveryInterval = value; }
    public int getWorkerCoreSize() { return workerCoreSize; }
    public void setWorkerCoreSize(int value) { this.workerCoreSize = value; }
    public int getWorkerMaxSize() { return workerMaxSize; }
    public void setWorkerMaxSize(int value) { this.workerMaxSize = value; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int value) { this.queueCapacity = value; }
    public Duration getConfirmationDelay() { return confirmationDelay; }
    public void setConfirmationDelay(Duration value) { this.confirmationDelay = value; }

    public void validate() {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("CORS Redis host must not be blank");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("CORS Redis port is invalid");
        if (database < 0) throw new IllegalArgumentException("CORS Redis database must not be negative");
        if (channel == null || channel.isBlank()) throw new IllegalArgumentException("CORS Redis channel must not be blank");
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || recoveryInterval == null || recoveryInterval.isNegative() || recoveryInterval.isZero()
                || confirmationDelay == null || confirmationDelay.isNegative()) {
            throw new IllegalArgumentException("CORS Redis durations are invalid");
        }
        if (workerCoreSize < 1 || workerMaxSize < workerCoreSize || queueCapacity < 1) {
            throw new IllegalArgumentException("CORS Redis worker settings are invalid");
        }
    }
}
