package systemdesign.distributedsystemsinternals;

import java.util.*;

/**
 * Distributed systems internals patterns for explaining coordination and correctness.
 */
public class DistributedSystemsInternalsExamples {

    static class QuorumCalculator {
        static boolean overlaps(int replicas, int readQuorum, int writeQuorum) {
            return readQuorum + writeQuorum > replicas;
        }
    }

    static class FencingTokenService {
        private long currentToken = 0;

        synchronized long nextToken() {
            currentToken++;
            return currentToken;
        }

        boolean isFresh(long token) {
            return token >= currentToken;
        }
    }

    static class IdempotentConsumer {
        private final Set<String> processed = new HashSet<>();

        boolean process(String messageId) {
            if (!processed.add(messageId)) {
                return false;
            }
            return true;
        }
    }

    public static void main(String[] args) {
        System.out.println("Quorum overlaps: " + QuorumCalculator.overlaps(3, 2, 2));

        FencingTokenService tokens = new FencingTokenService();
        long token1 = tokens.nextToken();
        long token2 = tokens.nextToken();
        System.out.println("Token1 fresh: " + tokens.isFresh(token1));
        System.out.println("Token2 fresh: " + tokens.isFresh(token2));

        IdempotentConsumer consumer = new IdempotentConsumer();
        System.out.println("First processing: " + consumer.process("msg-1"));
        System.out.println("Second processing: " + consumer.process("msg-1"));
    }
}
