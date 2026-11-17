package dev.punjarrr.autoItem.refill;

import dev.punjarrr.autoItem.config.PluginConfig;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static dev.punjarrr.autoItem.refill.FakeItem.key;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** U27: the remainders table and its inverse. */
class RemaindersTest {

    private final Remainders defaults = new Remainders(PluginConfig.defaults().remainders());

    @Test
    void forwardRemainders() {
        assertEquals(Set.of(key("bowl")), defaults.allowedFor(key("mushroom_stew")));
        assertEquals(Set.of(key("glass_bottle")), defaults.allowedFor(key("potion")));
        assertEquals(Set.of(key("glass_bottle")), defaults.allowedFor(key("honey_bottle")));
        assertEquals(Set.of(key("bucket")), defaults.allowedFor(key("axolotl_bucket")));
    }

    @Test
    void noneAndUnknownLeaveNothing() {
        assertEquals(Set.of(), defaults.allowedFor(key("ominous_bottle")));
        assertEquals(Set.of(), defaults.allowedFor(key("cobblestone")));
    }

    @Test
    void inverseForFilling() {
        assertEquals(Set.of(key("water_bucket"), key("lava_bucket"), key("milk_bucket"), key("powder_snow_bucket"),
                key("cod_bucket"), key("salmon_bucket"), key("pufferfish_bucket"), key("tropical_fish_bucket"),
                key("axolotl_bucket"), key("tadpole_bucket")), defaults.allowedFor(key("bucket")));
        assertEquals(Set.of(key("potion"), key("honey_bottle")), defaults.allowedFor(key("glass_bottle")));
        assertEquals(Set.of(key("mushroom_stew"), key("rabbit_stew"), key("beetroot_soup"), key("suspicious_stew")),
                defaults.allowedFor(key("bowl")));
    }
}
