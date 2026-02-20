package net.questz.quest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.questz.QuestzMain;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class QuestHandler {

    public static void createAdvancement(MinecraftServer server, String jsonContent, String fileName, @Nullable String existingAdvancementId) {
        Path datapackPath = server.getSavePath(WorldSavePath.ROOT).resolve("datapacks/questz_generated");
        Path advancementPath = datapackPath.resolve("data/questz/advancement/quests/" + fileName + ".json");

        try {
            if (existingAdvancementId != null) {
                Path existingAdvancementPath = datapackPath.resolve("data/questz/advancement/" + existingAdvancementId + ".json");
                Files.deleteIfExists(existingAdvancementPath);
            }

            Files.createDirectories(advancementPath.getParent());
            Files.writeString(advancementPath, jsonContent);

            createPackMcmeta(datapackPath);

            // Reload triggern
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "reload");
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

    public static void deleteAdvancement(MinecraftServer server, Identifier advancementId) {
        try {
            Path datapackPath = server.getSavePath(WorldSavePath.ROOT).resolve("datapacks/questz_generated");
            Path advancementPath = datapackPath.resolve("data/questz/advancement/" + advancementId.getPath() + ".json");

            try {
                Files.deleteIfExists(advancementPath);

                // Reload triggern
                server.getCommandManager().executeWithPrefix(server.getCommandSource(), "reload");
            } catch (IOException e) {
                QuestzMain.LOGGER.error("Failed to delete file", e);
            }

        } catch (Exception e) {
            QuestzMain.LOGGER.error("Failed to delete file", e);
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
