package dev.smpcore.settings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListSettingTest {

    @Test
    void parsesCommaAndNewlineSeparatedText() {
        assertEquals(List.of("mace", "netherite_sword", "totem_of_undying:2"),
                ListSetting.parse(" mace, netherite_sword\ntotem_of_undying:2 ,, "));
        assertEquals(List.of(), ListSetting.parse("   "));
    }

    @Test
    void normalizesNamespaces() {
        assertEquals("end_crystal", ListSetting.normalize("minecraft:END_CRYSTAL"));
        assertEquals("custom:thing", ListSetting.normalize(" custom:Thing "));
    }
}
