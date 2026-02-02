package com.codehows.daehobe.common;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.logging.Logger;

/**
 * JUnit 5 Extension - 각 테스트 메서드의 실행 시간(ms) 및 메모리 사용량(MB)을 측정하여 로그로 출력
 */
public class PerformanceLoggingExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Logger log = Logger.getLogger(PerformanceLoggingExtension.class.getName());

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(PerformanceLoggingExtension.class);

    private static final String START_TIME = "startTime";
    private static final String START_MEMORY = "startMemory";

    @Override
    public void beforeEach(ExtensionContext context) {
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        context.getStore(NAMESPACE).put(START_TIME, System.nanoTime());
        context.getStore(NAMESPACE).put(START_MEMORY, runtime.totalMemory() - runtime.freeMemory());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        long startTime = context.getStore(NAMESPACE).get(START_TIME, Long.class);
        long startMemory = context.getStore(NAMESPACE).get(START_MEMORY, Long.class);

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

        Runtime runtime = Runtime.getRuntime();
        long endMemory = runtime.totalMemory() - runtime.freeMemory();
        double memoryUsedMb = (endMemory - startMemory) / (1024.0 * 1024.0);

        String testName = context.getDisplayName();
        String className = context.getRequiredTestClass().getSimpleName();

        log.info(String.format(
                "[Performance] %s.%s - Time: %d ms | Memory: %.2f MB",
                className, testName, elapsedMs, memoryUsedMb
        ));
    }
}
