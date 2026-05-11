package org.example.order.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Configuration
@EnableConfigurationProperties(OrderRedissonProperties.class)
public class RedissonConfiguration {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(OrderRedissonProperties properties) {
        Config config = new Config();
        String mode = properties.getMode() == null ? "cluster" : properties.getMode().trim().toLowerCase(Locale.ROOT);
        if ("single".equals(mode)) {
            configureSingle(config, properties);
        } else {
            configureCluster(config, properties);
        }
        return Redisson.create(config);
    }

    private void configureSingle(Config config, OrderRedissonProperties properties) {
        SingleServerConfig single = config.useSingleServer()
                .setAddress(properties.getSingleServerAddress())
                .setTimeout((int) properties.getTimeout().toMillis())
                .setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        if (StringUtils.hasText(properties.getPassword())) {
            single.setPassword(properties.getPassword());
        }
    }

    private void configureCluster(Config config, OrderRedissonProperties properties) {
        ClusterServersConfig cluster = config.useClusterServers()
                .setTimeout((int) properties.getTimeout().toMillis())
                .setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        for (String node : properties.getClusterNodes()) {
            cluster.addNodeAddress(node);
        }
        if (StringUtils.hasText(properties.getPassword())) {
            cluster.setPassword(properties.getPassword());
        }
    }
}

