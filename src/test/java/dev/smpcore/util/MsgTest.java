package dev.smpcore.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MsgTest {

    @Test
    void parsesDurations() {
        assertEquals(30_000L, Msg.parseDuration("30s"));
        assertEquals(600_000L, Msg.parseDuration("10m"));
        assertEquals(7_200_000L, Msg.parseDuration("2h"));
        assertEquals(86_400_000L * 7, Msg.parseDuration("7d"));
        assertEquals(86_400_000L * 7, Msg.parseDuration("1w"));
        assertEquals(3_600_000L + 1_800_000L, Msg.parseDuration("1h30m"));
        assertEquals(5 * 60_000L, Msg.parseDuration("5"), "bare numbers are minutes");
    }

    @Test
    void permanentAndInvalidDurations() {
        assertEquals(-1L, Msg.parseDuration("perm"));
        assertEquals(-1L, Msg.parseDuration("permanent"));
        assertEquals(0L, Msg.parseDuration("griefing"));
        assertEquals(0L, Msg.parseDuration(""));
    }

    @Test
    void formatsDurations() {
        assertEquals("0s", Msg.duration(0));
        assertEquals("1m 5s", Msg.duration(65_000));
        assertEquals("1d 2h", Msg.duration(93_600_000));
    }

    @Test
    void prettifiesEnumNames() {
        assertEquals("Netherite Sword", Msg.pretty("NETHERITE_SWORD"));
        assertEquals("Totem Of Undying", Msg.pretty("minecraft:totem_of_undying"));
    }
}
