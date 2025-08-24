package Nivkida.Manager;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SelectedItemsManager {
    private static final Set<ResourceLocation> SELECTED = new HashSet<>();

    public static void toggle(ResourceLocation id) {
        if (SELECTED.contains(id)) SELECTED.remove(id);
        else SELECTED.add(id);
    }

    public static boolean isSelected(ResourceLocation id) {
        return SELECTED.contains(id);
    }

    public static Set<ResourceLocation> getAll() {
        return Collections.unmodifiableSet(SELECTED);
    }

    public static void clear() {
        SELECTED.clear();
    }
}