package net.questz.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.questz.QuestzMain;
import net.questz.book.Chapter;
import net.questz.book.QuestBook;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class QuestStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static Path getChapterDirectory(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("questz").resolve("chapters");
    }

    public static QuestBook load(MinecraftServer server) {
        Path directory = getChapterDirectory(server);
        if (!Files.isDirectory(directory)) {
            return QuestBook.EMPTY;
        }
        RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, server.getRegistryManager());
        List<Chapter> chapters = new ArrayList<>();

        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    JsonElement json = JsonParser.parseReader(reader);
                    Chapter.CODEC.parse(ops, json)
                            .resultOrPartial(error -> {
                                QuestzMain.LOGGER.error("Failed to parse quest chapter {}: {}", file.getFileName(), error);
                                backup(file);
                            })
                            .ifPresent(chapter -> {
                                if (!file.getFileName().toString().equals(chapter.id() + ".json")) {
                                    QuestzMain.LOGGER.warn("Quest chapter file {} contains chapter id {}", file.getFileName(), chapter.id());
                                }
                                chapters.add(chapter);
                            });
                } catch (Exception exception) {
                    QuestzMain.LOGGER.error("Failed to read quest chapter {}", file.getFileName(), exception);
                }
            }
        } catch (IOException exception) {
            QuestzMain.LOGGER.error("Failed to list quest chapters in {}", directory, exception);
        }

        QuestBook book = new QuestBook(chapters);
        book.getProblems().forEach(problem -> QuestzMain.LOGGER.warn("Quest book problem: {}", problem));
        return book;
    }

    private static void backup(Path file) {
        Path backup = file.resolveSibling(file.getFileName() + ".bak");
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            QuestzMain.LOGGER.warn("Saved a backup of {} to {}", file.getFileName(), backup.getFileName());
        } catch (IOException exception) {
            QuestzMain.LOGGER.error("Failed to back up quest chapter {}", file.getFileName(), exception);
        }
    }

    public static boolean saveChapter(MinecraftServer server, Chapter chapter) {
        RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, server.getRegistryManager());
        JsonElement json = Chapter.CODEC.encodeStart(ops, chapter)
                .resultOrPartial(error -> QuestzMain.LOGGER.error("Failed to encode quest chapter {}: {}", chapter.id(), error))
                .orElse(null);
        if (json == null) {
            return false;
        }

        Path directory = getChapterDirectory(server);
        Path file = directory.resolve(chapter.id() + ".json");
        Path temporary = directory.resolve(chapter.id() + ".json.tmp");
        try {
            Files.createDirectories(directory);
            Files.writeString(temporary, GSON.toJson(json));
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            QuestzMain.LOGGER.error("Failed to save quest chapter {}", chapter.id(), exception);
            return false;
        }
    }

    public static boolean deleteChapter(MinecraftServer server, String chapterId) {
        try {
            Files.deleteIfExists(getChapterDirectory(server).resolve(chapterId + ".json"));
            return true;
        } catch (IOException exception) {
            QuestzMain.LOGGER.error("Failed to delete quest chapter {}", chapterId, exception);
            return false;
        }
    }

}
