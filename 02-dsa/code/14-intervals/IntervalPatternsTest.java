package dsa.intervals;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for IntervalPatterns — covers all 7 interval problem types.
 */
class IntervalPatternsTest {

    private final IntervalPatterns sol = new IntervalPatterns();

    // ── Merge Intervals (LC 56) ───────────────────────────────────────────────

    @Test
    void merge_overlapping() {
        int[][] result = sol.merge(new int[][]{{1,3},{2,6},{8,10},{15,18}});
        assertThat(result).isDeepEqualTo(new int[][]{{1,6},{8,10},{15,18}});
    }

    @Test
    void merge_touchingButNotOverlapping() {
        int[][] result = sol.merge(new int[][]{{1,4},{4,5}});
        assertThat(result).isDeepEqualTo(new int[][]{{1,5}});
    }

    @Test
    void merge_noOverlap() {
        int[][] result = sol.merge(new int[][]{{1,2},{3,4}});
        assertThat(result).isDeepEqualTo(new int[][]{{1,2},{3,4}});
    }

    @Test
    void merge_singleInterval() {
        int[][] result = sol.merge(new int[][]{{1,5}});
        assertThat(result).isDeepEqualTo(new int[][]{{1,5}});
    }

    // ── Insert Interval (LC 57) ───────────────────────────────────────────────

    @Test
    void insert_inMiddle() {
        int[][] result = sol.insert(new int[][]{{1,2},{3,5},{6,7},{8,10}}, new int[]{4,8});
        assertThat(result).isDeepEqualTo(new int[][]{{1,2},{3,10}});
    }

    @Test
    void insert_noOverlap_atStart() {
        int[][] result = sol.insert(new int[][]{{3,5},{6,9}}, new int[]{1,2});
        assertThat(result).isDeepEqualTo(new int[][]{{1,2},{3,5},{6,9}});
    }

    @Test
    void insert_coveredByNew() {
        int[][] result = sol.insert(new int[][]{{1,3},{6,9}}, new int[]{2,5});
        assertThat(result).isDeepEqualTo(new int[][]{{1,5},{6,9}});
    }

    // ── Non-overlapping Intervals (LC 435) ────────────────────────────────────

    @Test
    void eraseOverlapIntervals_one() {
        assertThat(sol.eraseOverlapIntervals(
            new int[][]{{1,2},{2,3},{3,4},{1,3}})).isEqualTo(1);
    }

    @Test
    void eraseOverlapIntervals_two() {
        assertThat(sol.eraseOverlapIntervals(
            new int[][]{{1,2},{1,2},{1,2}})).isEqualTo(2);
    }

    @Test
    void eraseOverlapIntervals_none() {
        assertThat(sol.eraseOverlapIntervals(
            new int[][]{{1,2},{2,3}})).isEqualTo(0);
    }

    // ── Meeting Rooms I (LC 252) ──────────────────────────────────────────────

    @Test
    void canAttendMeetings_overlap() {
        assertThat(sol.canAttendMeetings(new int[][]{{0,30},{5,10},{15,20}})).isFalse();
    }

    @Test
    void canAttendMeetings_noOverlap() {
        assertThat(sol.canAttendMeetings(new int[][]{{7,10},{2,4}})).isTrue();
    }

    // ── Meeting Rooms II (LC 253) ─────────────────────────────────────────────

    @Test
    void minMeetingRooms_two() {
        assertThat(sol.minMeetingRooms(new int[][]{{0,30},{5,10},{15,20}})).isEqualTo(2);
        assertThat(sol.minMeetingRoomsHeap(new int[][]{{0,30},{5,10},{15,20}})).isEqualTo(2);
    }

    @Test
    void minMeetingRooms_three() {
        assertThat(sol.minMeetingRooms(new int[][]{{0,10},{0,10},{0,10}})).isEqualTo(3);
    }

    @Test
    void minMeetingRooms_one() {
        assertThat(sol.minMeetingRooms(new int[][]{{1,5},{6,10}})).isEqualTo(1);
    }

    // ── Interval Intersection (LC 986) ────────────────────────────────────────

    @Test
    void intervalIntersection_standard() {
        int[][] result = sol.intervalIntersection(
            new int[][]{{0,2},{5,10},{13,23},{24,25}},
            new int[][]{{1,5},{8,12},{15,24},{25,26}});
        assertThat(result).isDeepEqualTo(
            new int[][]{{1,2},{5,5},{8,10},{15,23},{24,24},{25,25}});
    }

    @Test
    void intervalIntersection_empty() {
        int[][] result = sol.intervalIntersection(new int[][]{}, new int[][]{{1,5}});
        assertThat(result).isEmpty();
    }
}
