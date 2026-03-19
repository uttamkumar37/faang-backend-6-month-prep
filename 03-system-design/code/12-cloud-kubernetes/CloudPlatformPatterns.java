package systemdesign.cloudkubernetes;

import java.util.Map;

/**
 * Cloud and Kubernetes runtime patterns for backend interviews.
 */
public class CloudPlatformPatterns {

    static class ProbePolicy {
        static boolean shouldFailReadiness(boolean dbHealthy, boolean queueHealthy) {
            return !(dbHealthy && queueHealthy);
        }

        static boolean shouldFailLiveness(boolean eventLoopStuck, boolean deadlockDetected) {
            return eventLoopStuck || deadlockDetected;
        }
    }

    static class HorizontalPodAutoscalingHeuristic {
        static int desiredReplicas(int currentReplicas, int currentCpuPercent, int targetCpuPercent) {
            int desired = (int) Math.ceil(currentReplicas * (currentCpuPercent / (double) targetCpuPercent));
            return Math.max(1, desired);
        }
    }

    static class ConfigSeparation {
        static String sourceForKey(String key) {
            Map<String, String> secretKeys = Map.of(
                "db.password", "Secret",
                "jwt.signingKey", "Secret"
            );
            return secretKeys.getOrDefault(key, "ConfigMap");
        }
    }

    public static void main(String[] args) {
        System.out.println("Fail readiness: " + ProbePolicy.shouldFailReadiness(true, false));
        System.out.println("Fail liveness: " + ProbePolicy.shouldFailLiveness(false, false));
        System.out.println("Desired replicas: " + HorizontalPodAutoscalingHeuristic.desiredReplicas(4, 85, 60));
        System.out.println("db.password source: " + ConfigSeparation.sourceForKey("db.password"));
    }
}
