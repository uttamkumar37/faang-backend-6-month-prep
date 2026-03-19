package systemdesign.databaseinternals;

import java.util.*;

/**
 * Database internals and tuning examples for interview discussion.
 */
public class DatabaseInternalsPatterns {

    static class CompositeIndexAdvisor {
        static List<String> recommend(String equalityColumn, String rangeColumn, String orderByColumn) {
            List<String> columns = new ArrayList<>();
            if (equalityColumn != null) {
                columns.add(equalityColumn);
            }
            if (rangeColumn != null && !Objects.equals(rangeColumn, equalityColumn)) {
                columns.add(rangeColumn);
            }
            if (orderByColumn != null && !columns.contains(orderByColumn)) {
                columns.add(orderByColumn);
            }
            return columns;
        }
    }

    static class ReplicaReadPolicy {
        static String route(boolean requiresReadYourWrites, long replicaLagMs) {
            if (requiresReadYourWrites || replicaLagMs > 200) {
                return "primary";
            }
            return "replica";
        }
    }

    static class MigrationPlanner {
        static List<String> expandAndContractPlan(String columnName) {
            return List.of(
                "add nullable column " + columnName,
                "deploy writers for old+new schema",
                "backfill historical rows in batches",
                "switch reads to new column",
                "remove old column in later deploy"
            );
        }
    }

    public static void main(String[] args) {
        System.out.println("Recommended index: " + CompositeIndexAdvisor.recommend("tenant_id", "created_at", "created_at"));
        System.out.println("Read route: " + ReplicaReadPolicy.route(true, 50));
        System.out.println("Migration plan: " + MigrationPlanner.expandAndContractPlan("normalized_email"));
    }
}
