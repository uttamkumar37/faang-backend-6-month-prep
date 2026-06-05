import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SplitwiseDesign {
    record User(String id, String name) {}
    record Split(User user, BigDecimal amount) {}
    record Expense(User paidBy, BigDecimal amount, List<Split> splits, String idempotencyKey) {}

    interface SplitStrategy {
        List<Split> split(User paidBy, BigDecimal amount, List<User> participants);
    }

    static final class EqualSplitStrategy implements SplitStrategy {
        public List<Split> split(User paidBy, BigDecimal amount, List<User> participants) {
            if (participants.isEmpty()) throw new IllegalArgumentException("participants required");
            BigDecimal share = amount.divide(BigDecimal.valueOf(participants.size()), 2, RoundingMode.HALF_UP);
            return participants.stream().map(user -> new Split(user, share)).toList();
        }
    }

    static final class BalanceSheet {
        private final Map<String, BigDecimal> balances = new HashMap<>();

        void apply(Expense expense) {
            BigDecimal total = expense.splits().stream().map(Split::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(expense.amount()) != 0) {
                throw new IllegalArgumentException("split total must match expense amount");
            }
            for (Split split : expense.splits()) {
                if (!split.user().equals(expense.paidBy())) {
                    addDebt(split.user(), expense.paidBy(), split.amount());
                }
            }
        }

        BigDecimal amountOwed(User from, User to) {
            return balances.getOrDefault(key(from, to), BigDecimal.ZERO);
        }

        private void addDebt(User from, User to, BigDecimal amount) {
            balances.merge(key(from, to), amount, BigDecimal::add);
            balances.merge(key(to, from), amount.negate(), BigDecimal::add);
        }

        private String key(User from, User to) {
            return from.id() + "->" + to.id();
        }
    }

    // Test ideas: equal split, exact split mismatch, payer share ignored, duplicate expense id handled by service layer.
}
