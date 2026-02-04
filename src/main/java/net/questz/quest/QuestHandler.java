package net.questz.quest;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public class QuestHandler {

    public static void handleAdvancementCreation(MinecraftServer server, String jsonContent, String fileName) {
        Path datapackPath = server.getSavePath(WorldSavePath.ROOT).resolve("datapacks/questz_generated");
        Path advancementPath = datapackPath.resolve("data/questz/advancements/" + fileName + ".json");

        try {
            Files.createDirectories(advancementPath.getParent());
            Files.writeString(advancementPath, jsonContent);

            createPackMcmeta(datapackPath);

            // Reload triggern
//            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "reload");
            server.reloadResources(Collections.singleton("questz_generated"));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createPackMcmeta(Path root) throws IOException {
        Path meta = root.resolve("pack.mcmeta");
        if (!Files.exists(meta)) {
            String content = "{\"pack\":{\"description\":\"Generated Quests\",\"pack_format\":15}}";
            Files.writeString(meta, content);
        }
    }

}
