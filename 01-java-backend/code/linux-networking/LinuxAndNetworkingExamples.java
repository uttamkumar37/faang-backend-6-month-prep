package com.faangprep.javabackend.linuxnetworking;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OS and networking patterns backend engineers should reason about clearly.
 */
public class LinuxAndNetworkingExamples {

    static class ThreadPoolSizing {
        static int cpuBoundThreads(int cpuCores) {
            return cpuCores;
        }

        static int ioBoundThreads(int cpuCores, int waitMs, int computeMs) {
            return cpuCores * (1 + waitMs / Math.max(1, computeMs));
        }
    }

    static class DeadlineBudget {
        private final long deadlineNanos;

        DeadlineBudget(Duration totalBudget) {
            this.deadlineNanos = System.nanoTime() + totalBudget.toNanos();
        }

        long remainingMillis() {
            long remaining = deadlineNanos - System.nanoTime();
            return Math.max(0, TimeUnit.NANOSECONDS.toMillis(remaining));
        }

        boolean expired() {
            return remainingMillis() == 0;
        }
    }

    static class ConnectionPoolGuard {
        private final Semaphore permits;
        private final AtomicInteger rejected = new AtomicInteger();

        ConnectionPoolGuard(int maxConcurrentCalls) {
            this.permits = new Semaphore(maxConcurrentCalls);
        }

        <T> T execute(Callable<T> task, long timeoutMs) throws Exception {
            if (!permits.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                rejected.incrementAndGet();
                throw new TimeoutException("Connection pool saturated");
            }
            try {
                return task.call();
            } finally {
                permits.release();
            }
        }

        int rejectedRequests() {
            return rejected.get();
        }
    }

    static class RetryPolicy {
        static long backoffWithJitterMillis(int attempt, long baseDelayMs) {
            long capped = Math.min(baseDelayMs * (1L << Math.min(attempt, 10)), 5_000);
            long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, capped / 2));
            return capped / 2 + jitter;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("CPU-bound thread count on 8 cores: " + ThreadPoolSizing.cpuBoundThreads(8));
        System.out.println("IO-bound thread count heuristic: " + ThreadPoolSizing.ioBoundThreads(8, 90, 10));

        DeadlineBudget budget = new DeadlineBudget(Duration.ofMillis(250));
        System.out.println("Remaining budget ms: " + budget.remainingMillis());

        ConnectionPoolGuard guard = new ConnectionPoolGuard(2);
        String result = guard.execute(() -> "ok", 100);
        System.out.println("Pool result: " + result);
        System.out.println("Retry delay attempt 3: " + RetryPolicy.backoffWithJitterMillis(3, 100));
    }
}
