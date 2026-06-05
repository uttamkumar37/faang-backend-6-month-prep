import java.time.Instant;
import java.util.List;

public class LoggingFrameworkDesign {
    enum Level { DEBUG, INFO, WARN, ERROR }
    record LogEvent(Instant timestamp, Level level, String logger, String message, Throwable throwable) {}

    interface Appender {
        void append(LogEvent event);
    }

    interface Formatter {
        String format(LogEvent event);
    }

    static final class SimpleFormatter implements Formatter {
        public String format(LogEvent event) {
            String base = "%s %-5s [%s] %s".formatted(event.timestamp(), event.level(), event.logger(), event.message());
            return event.throwable() == null ? base : base + " :: " + event.throwable().getClass().getSimpleName();
        }
    }

    static final class ConsoleAppender implements Appender {
        private final Formatter formatter;
        ConsoleAppender(Formatter formatter) { this.formatter = formatter; }
        public void append(LogEvent event) { System.out.println(formatter.format(event)); }
    }

    static final class Logger {
        private final String name;
        private final Level threshold;
        private final List<Appender> appenders;

        Logger(String name, Level threshold, List<Appender> appenders) {
            this.name = name;
            this.threshold = threshold;
            this.appenders = List.copyOf(appenders);
        }

        void log(Level level, String message, Throwable throwable) {
            if (level.ordinal() < threshold.ordinal()) return;
            var event = new LogEvent(Instant.now(), level, name, message, throwable);
            for (Appender appender : appenders) {
                try {
                    appender.append(event);
                } catch (RuntimeException ignored) {
                    // Appender failures should not crash business code in this skeleton.
                }
            }
        }

        void info(String message) { log(Level.INFO, message, null); }
        void error(String message, Throwable throwable) { log(Level.ERROR, message, throwable); }
    }

    // Test ideas: DEBUG filtered at INFO threshold, multiple appenders, appender exception isolation, throwable formatting.
}
