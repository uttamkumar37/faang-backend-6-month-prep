package com.faangprep.javabackend.testingdelivery;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Testing and release engineering patterns expressed in simple Java code.
 */
public class TestingAndReleasePatterns {

    static class DeterministicRetryPolicy {
        static Duration nextDelay(int attempt) {
            long millis = Math.min(100L * (1L << Math.min(attempt, 6)), 3_000L);
            return Duration.ofMillis(millis);
        }
    }

    static class FeatureFlagService {
        private final Map<String, Boolean> flags = new ConcurrentHashMap<>();

        void setFlag(String name, boolean enabled) {
            flags.put(name, enabled);
        }

        boolean isEnabled(String name) {
            return flags.getOrDefault(name, false);
        }
    }

    static class CompatibilityMigration {
        static String readDisplayName(String oldColumn, String newColumn) {
            if (newColumn != null && !newColumn.isBlank()) {
                return newColumn;
            }
            return oldColumn;
        }
    }

    public static void main(String[] args) {
        System.out.println("Retry delay attempt 0: " + DeterministicRetryPolicy.nextDelay(0));
        System.out.println("Retry delay attempt 4: " + DeterministicRetryPolicy.nextDelay(4));

        FeatureFlagService flags = new FeatureFlagService();
        flags.setFlag("new-checkout-flow", true);
        System.out.println("Flag enabled: " + flags.isEnabled("new-checkout-flow"));

        System.out.println("Display name: " + CompatibilityMigration.readDisplayName("alice", ""));
    }
}
