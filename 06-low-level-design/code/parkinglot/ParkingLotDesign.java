import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ParkingLotDesign {
    enum VehicleType { BIKE, CAR, TRUCK }
    enum SpotType { BIKE, COMPACT, LARGE }
    enum TicketStatus { ACTIVE, CLOSED }

    record Vehicle(String number, VehicleType type) {}

    static final class ParkingSpot {
        private final String id;
        private final SpotType type;
        private boolean occupied;

        ParkingSpot(String id, SpotType type) {
            this.id = id;
            this.type = type;
        }

        boolean canFit(Vehicle vehicle) {
            return switch (vehicle.type()) {
                case BIKE -> true;
                case CAR -> type == SpotType.COMPACT || type == SpotType.LARGE;
                case TRUCK -> type == SpotType.LARGE;
            };
        }
    }

    record Ticket(String id, Vehicle vehicle, ParkingSpot spot, Instant entryTime, TicketStatus status) {}
    record Receipt(String ticketId, BigDecimal fee, Duration parkedFor) {}

    interface FeeCalculator {
        BigDecimal fee(Duration duration, VehicleType type);
    }

    static final class HourlyFeeCalculator implements FeeCalculator {
        public BigDecimal fee(Duration duration, VehicleType type) {
            long hours = Math.max(1, (long) Math.ceil(duration.toMinutes() / 60.0));
            long rate = switch (type) { case BIKE -> 10; case CAR -> 30; case TRUCK -> 60; };
            return BigDecimal.valueOf(hours * rate);
        }
    }

    static final class ParkingLot {
        private final List<ParkingSpot> spots = new ArrayList<>();
        private final Set<String> closedTickets = new HashSet<>();
        private final FeeCalculator feeCalculator;

        ParkingLot(List<ParkingSpot> spots, FeeCalculator feeCalculator) {
            this.spots.addAll(spots);
            this.feeCalculator = feeCalculator;
        }

        Ticket park(Vehicle vehicle, Instant now) {
            ParkingSpot spot = findSpot(vehicle).orElseThrow(() -> new IllegalStateException("No compatible spot available"));
            spot.occupied = true;
            return new Ticket(UUID.randomUUID().toString(), vehicle, spot, now, TicketStatus.ACTIVE);
        }

        Receipt exit(Ticket ticket, Instant now) {
            if (ticket.status() != TicketStatus.ACTIVE || closedTickets.contains(ticket.id())) {
                throw new IllegalStateException("Ticket already closed");
            }
            ticket.spot().occupied = false;
            closedTickets.add(ticket.id());
            Duration parkedFor = Duration.between(ticket.entryTime(), now);
            return new Receipt(ticket.id(), feeCalculator.fee(parkedFor, ticket.vehicle().type()), parkedFor);
        }

        private Optional<ParkingSpot> findSpot(Vehicle vehicle) {
            return spots.stream()
                    .filter(spot -> !spot.occupied && spot.canFit(vehicle))
                    .min(Comparator.comparing(spot -> spot.type.ordinal()));
        }
    }

    // Test ideas: no compatible spot, duplicate exit, truck only uses large, minimum one-hour fee.
}
