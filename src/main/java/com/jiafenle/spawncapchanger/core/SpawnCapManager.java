package com.jiafenle.spawncapchanger.core;

import com.jiafenle.spawncapchanger.SpawnCapChangerMod;
import com.jiafenle.spawncapchanger.data.SpawnCapStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.MobCategory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Actually changes the per-chunk instance cap stored inside {@link MobCategory}.
 * <p>
 * Minecraft's natural spawner ({@code NaturalSpawner}) reads
 * {@link MobCategory#getMaxInstancesPerChunk()} on every spawn cycle, so mutating the backing
 * field at runtime takes effect immediately without a restart or world reload.
 * <p>
 * The field is {@code private final} in vanilla and there is no public API to change it, so we
 * locate it reflectively. To stay resilient against mapping/name changes the field is identified
 * by its semantics: it is the only non-static {@code int} field whose value equals
 * {@code getMaxInstancesPerChunk()} for <em>every</em> enum constant (the other int field is the
 * despawn distance, which always differs from the cap).
 */
public final class SpawnCapManager {

    /** Category whose cap natural spawning does not consult; excluded from "set all". */
    public static final String MISC = "misc";

    private static final Map<MobCategory, Integer> VANILLA_DEFAULTS = new EnumMap<>(MobCategory.class);
    private static final Field CAP_FIELD;

    private static SpawnCapStorage storage;

    static {
        for (MobCategory category : MobCategory.values()) {
            VANILLA_DEFAULTS.put(category, category.getMaxInstancesPerChunk());
        }
        CAP_FIELD = findCapField();
        if (CAP_FIELD == null) {
            SpawnCapChangerMod.LOGGER.error(
                    "[Spawn Cap Changer] Could not locate the spawn-cap field inside MobCategory. "
                            + "Caps cannot be changed on this Minecraft version.");
        }
    }

    private SpawnCapManager() {
    }

    private static Field findCapField() {
        try {
            for (Field field : MobCategory.class.getDeclaredFields()) {
                if (field.getType() != int.class || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                if (holdsCapForEveryCategory(field)) {
                    return field;
                }
            }
        } catch (SecurityException | ReflectiveOperationException e) {
            SpawnCapChangerMod.LOGGER.error("[Spawn Cap Changer] Reflection setup failed", e);
        }
        return null;
    }

    private static boolean holdsCapForEveryCategory(Field field) throws IllegalAccessException {
        for (MobCategory category : MobCategory.values()) {
            if (field.getInt(category) != category.getMaxInstancesPerChunk()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Attaches the per-save storage, resets every category to the vanilla default first (so worlds
     * cannot leak caps into each other), then re-applies the persisted caps of this save.
     */
    public static void attach(MinecraftServer server) {
        storage = SpawnCapStorage.get(server);

        Map<String, Integer> saved = new HashMap<>(storage.getCaps());

        for (MobCategory category : MobCategory.values()) {
            writeCap(category, VANILLA_DEFAULTS.get(category));
        }

        int applied = 0;
        for (Map.Entry<String, Integer> entry : saved.entrySet()) {
            MobCategory category = byName(entry.getKey());
            if (category != null && writeCap(category, entry.getValue())) {
                applied++;
            }
        }
        SpawnCapChangerMod.LOGGER.info(
                "[Spawn Cap Changer] World attached; {} saved spawn cap override(s) applied.", applied);
    }

    /**
     * Changes a category cap immediately and remembers it for this save.
     *
     * @return {@code false} if the internal field could not be written.
     */
    public static boolean setCap(MobCategory category, int value) {
        if (!writeCap(category, value)) {
            return false;
        }
        if (storage != null) {
            storage.put(category.getSerializedName(), value);
        }
        return true;
    }

    /**
     * Restores one category's vanilla default and forgets its override.
     */
    public static boolean resetCap(MobCategory category) {
        if (!writeCap(category, VANILLA_DEFAULTS.get(category))) {
            return false;
        }
        if (storage != null) {
            storage.remove(category.getSerializedName());
        }
        return true;
    }

    /**
     * Restores every category's vanilla default and clears the persisted overrides.
     */
    public static boolean resetAll() {
        boolean ok = true;
        for (MobCategory category : MobCategory.values()) {
            ok &= writeCap(category, VANILLA_DEFAULTS.get(category));
        }
        if (storage != null) {
            storage.clear();
        }
        return ok;
    }

    private static boolean writeCap(MobCategory category, int value) {
        if (CAP_FIELD == null) {
            return false;
        }
        try {
            CAP_FIELD.setInt(category, value);
            return true;
        } catch (IllegalAccessException e) {
            SpawnCapChangerMod.LOGGER.error(
                    "[Spawn Cap Changer] Failed to write the spawn cap of {} (target value {})",
                    category.getSerializedName(), value, e);
            return false;
        }
    }

    public static boolean isAvailable() {
        return CAP_FIELD != null;
    }

    public static int getCap(MobCategory category) {
        return category.getMaxInstancesPerChunk();
    }

    public static int getVanillaDefault(MobCategory category) {
        return VANILLA_DEFAULTS.get(category);
    }

    public static MobCategory byName(String name) {
        if (name == null) {
            return null;
        }
        for (MobCategory category : MobCategory.values()) {
            if (category.getSerializedName().equals(name)) {
                return category;
            }
        }
        return null;
    }
}
