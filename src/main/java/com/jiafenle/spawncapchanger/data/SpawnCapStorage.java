package com.jiafenle.spawncapchanger.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-save persistence of the overridden spawn caps.
 * <p>
 * Stored on the overworld's {@link net.minecraft.world.level.storage.DimensionDataStorage}
 * under {@link #DATA_NAME}, so the chosen caps survive server restarts with the world.
 */
public class SpawnCapStorage extends SavedData {

    public static final String DATA_NAME = "spawncapchanger_caps";

    private static final String TAG_CAPS = "caps";

    private final Map<String, Integer> caps = new HashMap<>();

    public static SpawnCapStorage create() {
        return new SpawnCapStorage();
    }

    public static SpawnCapStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        SpawnCapStorage data = new SpawnCapStorage();
        CompoundTag capsTag = tag.getCompound(TAG_CAPS);
        for (String key : capsTag.getAllKeys()) {
            data.caps.put(key, capsTag.getInt(key));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag capsTag = new CompoundTag();
        this.caps.forEach(capsTag::putInt);
        tag.put(TAG_CAPS, capsTag);
        return tag;
    }

    /**
     * Gets (or creates) the storage instance attached to the overworld of the given server.
     */
    public static SpawnCapStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SpawnCapStorage::create, SpawnCapStorage::load, null),
                DATA_NAME
        );
    }

    public Map<String, Integer> getCaps() {
        return Collections.unmodifiableMap(this.caps);
    }

    public void put(String category, int cap) {
        this.caps.put(category, cap);
        this.setDirty();
    }

    public void remove(String category) {
        if (this.caps.remove(category) != null) {
            this.setDirty();
        }
    }

    public void clear() {
        if (!this.caps.isEmpty()) {
            this.caps.clear();
            this.setDirty();
        }
    }
}
