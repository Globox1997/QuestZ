package net.questz.quest;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public class QuestHandler {

    public static final Logger LOGGER = LoggerFactory.getLogger("questz");

    public static void handleAdvancementCreation(MinecraftServer server, String jsonContent, String fileName) {
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

    private static void createPackMcmeta(Path root) throws IOException {
        Path meta = root.resolve("pack.mcmeta");
        if (!Files.exists(meta)) {
            String content = "{\"pack\":{\"description\":\"Generated Quests\",\"pack_format\":48}}";
            Files.writeString(meta, content);
        }
    }

}
