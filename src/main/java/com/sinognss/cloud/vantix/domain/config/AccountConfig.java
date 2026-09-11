package com.sinognss.cloud.vantix.domain.config;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("account_config")
public class AccountConfig {
    @TableId
    private Long id;
    private Integer accountSilenceMonths;
    private Long updatedBy;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getAccountSilenceMonths() { return accountSilenceMonths; }
    public void setAccountSilenceMonths(Integer accountSilenceMonths) { this.accountSilenceMonths = accountSilenceMonths; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
