package org.example.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.redisson")
public class InventoryRedissonProperties {

    private String mode = "cluster";
    private List<String> clusterNodes = new ArrayList<>(List.of(
            "redis://localhost:7000",
            "redis://localhost:7001",
            "redis://localhost:7002",
            "redis://localhost:7003",
            "redis://localhost:7004",
            "redis://localhost:7005"
    ));
    private String singleServerAddress = "redis://localhost:6379";
    private String password;
    private Duration timeout = Duration.ofSeconds(3);
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration lockWaitTime = Duration.ofSeconds(2);
    private Duration lockLeaseTime = Duration.ofSeconds(10);

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<String> getClusterNodes() {
        return clusterNodes;
    }

    public void setClusterNodes(List<String> clusterNodes) {
        this.clusterNodes = clusterNodes;
    }

    public String getSingleServerAddress() {
        return singleServerAddress;
    }

    public void setSingleServerAddress(String singleServerAddress) {
        this.singleServerAddress = singleServerAddress;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getLockWaitTime() {
        return lockWaitTime;
    }

    public void setLockWaitTime(Duration lockWaitTime) {
        this.lockWaitTime = lockWaitTime;
    }

    public Duration getLockLeaseTime() {
        return lockLeaseTime;
    }

    public void setLockLeaseTime(Duration lockLeaseTime) {
        this.lockLeaseTime = lockLeaseTime;
    }
}

