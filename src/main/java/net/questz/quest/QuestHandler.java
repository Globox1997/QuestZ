package net.questz.quest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.questz.QuestzMain;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class QuestHandler {

    public static void createAdvancement(MinecraftServer server, String jsonContent, String fileName) {
        Path datapackPath = server.getSavePath(WorldSavePath.ROOT).resolve("datapacks/questz_generated");
        Path advancementPath = datapackPath.resolve("data/questz/advancement/quests/" + fileName + ".json");

        try {
            Files.createDirectories(advancementPath.getParent());
            Files.writeString(advancementPath, jsonContent);

            createPackMcmeta(datapackPath);

            // Reload triggern
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "reload");
//            server.reloadResources(Collections.singleton("questz_generated"));
//            server.reloadResources(Collections.singleton("questz_generated")).exceptionally(throwable -> {
//                LOGGER.warn("Failed to execute reload", throwable);
//                return null;
//            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void updateAdvancementPosition(MinecraftServer server, Identifier advancementId, float x, float y) {
        try {
            Path datapackPath = server.getSavePath(WorldSavePath.ROOT).resolve("datapacks/questz_generated");
            Path advancementPath = datapackPath.resolve("data/questz/advancement/" + advancementId.getPath() + ".json");

            JsonObject json = JsonParser.parseReader(Files.newBufferedReader(advancementPath)).getAsJsonObject();

            if (json.has("display")) {
                JsonObject display = json.getAsJsonObject("display");
                display.addProperty("x_manual", x);
                display.addProperty("y_manual", y);
            }

            try (Writer writer = Files.newBufferedWriter(advancementPath)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
            }

            QuestzMain.LOGGER.info("Saved position to file: {}", advancementPath);
        } catch (Exception e) {
            QuestzMain.LOGGER.error("Failed to save position to file", e);
        }
    }

    private static void createPackMcmeta(Path root) throws IOException {
        Path meta = root.resolve("pack.mcmeta");
        if (!Files.exists(meta)) {
            String content = "{\"pack\":{\"description\":\"Generated Quests\",\"pack_format\":48}}";
            Files.writeString(meta, content);
        }
    }

}
