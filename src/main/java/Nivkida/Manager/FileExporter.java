package Nivkida.Manager;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

public class FileExporter {

    private static File getBaseDir() {
        File gameDir = Minecraft.getInstance().gameDirectory;
        File cfg = new File(gameDir, "itemidcollect_exports");
        if (!cfg.exists()) cfg.mkdirs();
        return cfg;
    }

    // Возвращает File при успехе, иначе null
    public static File exportToTxt(String fileName, Set<ResourceLocation> ids) {
        File out = new File(getBaseDir(), safeFileName(fileName) + ".txt");
        try (FileWriter writer = new FileWriter(out)) {
            for (ResourceLocation id : ids) {
                writer.write("\"" + id.toString() + "\",\n");
            }
            writer.flush();
            return out;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static File exportToJson(String fileName, Set<ResourceLocation> ids) {
        File out = new File(getBaseDir(), safeFileName(fileName) + ".json");
        try (FileWriter writer = new FileWriter(out)) {
            writer.write("[\n");
            boolean first = true;
            for (ResourceLocation id : ids) {
                if (!first) writer.write(",\n");
                writer.write("  \"" + id.toString() + "\"");
                first = false;
            }
            writer.write("\n]");
            writer.flush();
            return out;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String safeFileName(String name) {
        if (name == null || name.isBlank()) return "items";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}

