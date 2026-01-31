package com.codehows.daehobe.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "async")
public class AsyncProperties {

    private ExecutorProperties stt = new ExecutorProperties(2, 4, 100, "stt-task-");
    private ExecutorProperties push = new ExecutorProperties(20, 100, 500, "push-async-");

    @Getter
    @Setter
    public static class ExecutorProperties {
        private int corePoolSize;
        private int maxPoolSize;
        private int queueCapacity;
        private String threadNamePrefix;

        public ExecutorProperties() {}

        public ExecutorProperties(int corePoolSize, int maxPoolSize, int queueCapacity, String threadNamePrefix) {
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.queueCapacity = queueCapacity;
            this.threadNamePrefix = threadNamePrefix;
        }
    }
}
