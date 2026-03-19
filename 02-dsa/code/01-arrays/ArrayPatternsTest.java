package dsa.arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ArrayPatterns — verifies core array algorithms.
 * These mirror actual LeetCode test cases to build confidence before interviews.
 */
class ArrayPatternsTest {

    // ── Prefix Sum ────────────────────────────────────────────────────────────

    @Test
    void rangeSum_subarray() {
        int[] prefix = ArrayPatterns.buildPrefix(new int[]{1, 2, 3, 4, 5});
        assertThat(ArrayPatterns.rangeSum(prefix, 1, 3)).isEqualTo(9); // 2+3+4
        assertThat(ArrayPatterns.rangeSum(prefix, 0, 4)).isEqualTo(15);
        assertThat(ArrayPatterns.rangeSum(prefix, 2, 2)).isEqualTo(3);
    }

    // ── Subarray Sum Equals K (LC 560) ────────────────────────────────────────

    @ParameterizedTest(name = "subarraySum({0}, k={1}) == {2}")
    @CsvSource({
        "'1,1,1', 2, 2",
        "'1,2,3', 3, 2",
        "'0,0,0', 0, 6"
    })
    void subarraySum(String input, int k, int expected) {
        int[] nums = parseInts(input);
        assertThat(ArrayPatterns.subarraySum(nums, k)).isEqualTo(expected);
    }

    // ── Valid Anagram (LC 242) ────────────────────────────────────────────────

    @Test
    void isAnagram_true() {
        assertThat(ArrayPatterns.isAnagram("anagram", "nagaram")).isTrue();
        assertThat(ArrayPatterns.isAnagram("", "")).isTrue();
    }

    @Test
    void isAnagram_false() {
        assertThat(ArrayPatterns.isAnagram("rat", "car")).isFalse();
        assertThat(ArrayPatterns.isAnagram("ab", "a")).isFalse();
    }

    // ── Group Anagrams (LC 49) ────────────────────────────────────────────────

    @Test
    void groupAnagrams_mixed() {
        List<List<String>> result = ArrayPatterns.groupAnagrams(
            new String[]{"eat","tea","tan","ate","nat","bat"});
        // Three groups: [eat,tea,ate], [tan,nat], [bat]
        assertThat(result).hasSize(3);
        assertThat(result.stream().anyMatch(g -> g.contains("bat") && g.size() == 1)).isTrue();
    }

    @Test
    void groupAnagrams_singleEmpty() {
        List<List<String>> result = ArrayPatterns.groupAnagrams(new String[]{""});
        assertThat(result).hasSize(1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int[] parseInts(String csv) {
        String[] parts = csv.split(",");
        int[] arr = new int[parts.length];
        for (int i = 0; i < parts.length; i++) arr[i] = Integer.parseInt(parts[i].trim());
        return arr;
    }
}
