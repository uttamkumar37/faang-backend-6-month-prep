import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

public class ElevatorSystemDesign {
    enum Direction { UP, DOWN, IDLE }
    enum DoorState { OPEN, CLOSED }
    record Request(int floor, Direction direction) {}

    static final class Elevator {
        final String id;
        int currentFloor;
        Direction direction = Direction.IDLE;
        DoorState doorState = DoorState.CLOSED;
        final Queue<Integer> stops = new ArrayDeque<>();

        Elevator(String id, int currentFloor) {
            this.id = id;
            this.currentFloor = currentFloor;
        }

        void addStop(int floor) {
            if (!stops.contains(floor)) stops.add(floor);
        }

        void tick() {
            Integer next = stops.peek();
            if (next == null) {
                direction = Direction.IDLE;
                doorState = DoorState.CLOSED;
                return;
            }
            doorState = DoorState.CLOSED;
            if (currentFloor < next) {
                currentFloor++;
                direction = Direction.UP;
            } else if (currentFloor > next) {
                currentFloor--;
                direction = Direction.DOWN;
            } else {
                stops.poll();
                doorState = DoorState.OPEN;
            }
        }
    }

    static final class NearestElevatorScheduler {
        Optional<Elevator> choose(List<Elevator> elevators, Request request) {
            return elevators.stream()
                    .filter(e -> e.direction == Direction.IDLE || e.direction == request.direction())
                    .min(Comparator.comparingInt(e -> Math.abs(e.currentFloor - request.floor())));
        }
    }

    static final class Controller {
        private final List<Elevator> elevators;
        private final NearestElevatorScheduler scheduler = new NearestElevatorScheduler();
        private final int minFloor;
        private final int maxFloor;

        Controller(List<Elevator> elevators, int minFloor, int maxFloor) {
            this.elevators = elevators;
            this.minFloor = minFloor;
            this.maxFloor = maxFloor;
        }

        void requestPickup(int floor, Direction direction) {
            if (floor < minFloor || floor > maxFloor) throw new IllegalArgumentException("invalid floor");
            scheduler.choose(elevators, new Request(floor, direction))
                    .orElseThrow(() -> new IllegalStateException("no elevator available"))
                    .addStop(floor);
        }
    }

    // Test ideas: invalid floor, nearest idle elevator, same-direction pickup, tick opens door at stop, duplicate stop.
}
