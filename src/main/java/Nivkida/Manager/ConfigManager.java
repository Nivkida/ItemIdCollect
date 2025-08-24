package Nivkida.Manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config/itemidcollect_config.json");

    public static class ConfigData {
        @SerializedName("cols")
        public int cols = 20;
        @SerializedName("rows")
        public int rows = 14;
        @SerializedName("last_mod")
        public String lastMod = "all";
        @SerializedName("last_search")
        public String lastSearch = "";
        @SerializedName("selected_types")
        public Set<String> selectedTypes = new HashSet<>();
    }

    private static ConfigData data;

    public static ConfigData load() {
        if (data != null) return data;
        try {
            if (CONFIG_FILE.exists()) {
                try (FileReader r = new FileReader(CONFIG_FILE)) {
                    data = GSON.fromJson(r, ConfigData.class);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (data == null) data = new ConfigData();
        return data;
    }

    public static void save(ConfigData d) {
        try {
            File cfgDir = CONFIG_FILE.getParentFile();
            if (!cfgDir.exists()) cfgDir.mkdirs();
            try (FileWriter w = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(d, w);
            }
            data = d;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}