import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BookMyShowDesign {
    enum BookingStatus { HELD, CONFIRMED, EXPIRED, CANCELLED }
    record Seat(String id, String row, int number) {}
    record Show(String id, String movieId, List<Seat> seats) {}
    record Booking(String id, String userId, String showId, List<String> seatIds, BookingStatus status) {}
    record SeatLock(String showId, String seatId, String userId, Instant expiresAt) {}

    static final class SeatLockManager {
        private final Map<String, SeatLock> locks = new HashMap<>();
        private final Clock clock;
        private final Duration ttl;

        SeatLockManager(Clock clock, Duration ttl) {
            this.clock = clock;
            this.ttl = ttl;
        }

        synchronized void lock(String showId, List<String> seatIds, String userId) {
            expireLocks();
            for (String seatId : seatIds) {
                SeatLock existing = locks.get(key(showId, seatId));
                if (existing != null && !existing.userId().equals(userId)) {
                    throw new IllegalStateException("seat already locked: " + seatId);
                }
            }
            for (String seatId : seatIds) {
                locks.put(key(showId, seatId), new SeatLock(showId, seatId, userId, clock.instant().plus(ttl)));
            }
        }

        synchronized boolean ownsValidLock(String showId, List<String> seatIds, String userId) {
            expireLocks();
            return seatIds.stream().allMatch(seatId -> {
                SeatLock lock = locks.get(key(showId, seatId));
                return lock != null && lock.userId().equals(userId);
            });
        }

        synchronized void release(String showId, List<String> seatIds) {
            seatIds.forEach(seatId -> locks.remove(key(showId, seatId)));
        }

        synchronized void expireLocks() {
            Instant now = clock.instant();
            locks.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
        }

        private String key(String showId, String seatId) {
            return showId + ":" + seatId;
        }
    }

    static final class BookingService {
        private final SeatLockManager lockManager;
        private final Map<String, Booking> bookings = new HashMap<>();
        private final Set<String> confirmedSeats = new HashSet<>();

        BookingService(SeatLockManager lockManager) {
            this.lockManager = lockManager;
        }

        synchronized Booking hold(String userId, String showId, List<String> seatIds) {
            if (seatIds.isEmpty()) throw new IllegalArgumentException("seatIds required");
            for (String seatId : seatIds) {
                if (confirmedSeats.contains(key(showId, seatId))) {
                    throw new IllegalStateException("seat already booked: " + seatId);
                }
            }
            lockManager.lock(showId, seatIds, userId);
            Booking booking = new Booking(UUID.randomUUID().toString(), userId, showId, List.copyOf(seatIds), BookingStatus.HELD);
            bookings.put(booking.id(), booking);
            return booking;
        }

        synchronized Booking confirm(String bookingId) {
            Booking booking = bookings.get(bookingId);
            if (booking == null) throw new IllegalArgumentException("unknown booking");
            if (booking.status() != BookingStatus.HELD) throw new IllegalStateException("booking not held");
            if (!lockManager.ownsValidLock(booking.showId(), booking.seatIds(), booking.userId())) {
                Booking expired = new Booking(booking.id(), booking.userId(), booking.showId(), booking.seatIds(), BookingStatus.EXPIRED);
                bookings.put(bookingId, expired);
                throw new IllegalStateException("seat lock expired");
            }
            lockManager.release(booking.showId(), booking.seatIds());
            booking.seatIds().forEach(seatId -> confirmedSeats.add(key(booking.showId(), seatId)));
            Booking confirmed = new Booking(booking.id(), booking.userId(), booking.showId(), booking.seatIds(), BookingStatus.CONFIRMED);
            bookings.put(bookingId, confirmed);
            return confirmed;
        }

        private String key(String showId, String seatId) {
            return showId + ":" + seatId;
        }
    }

    // Test ideas: concurrent lock conflict, lock expiry before confirm, duplicate confirm, empty seat list, confirmed seat cannot be held again.
}
