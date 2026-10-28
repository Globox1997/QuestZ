package net.questz.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.questz.QuestzMain;
import net.questz.book.Chapter;
import net.questz.book.QuestBook;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class QuestStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static Path getChapterDirectory(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("questz").resolve("chapters");
    }

    public static Path getDefaultDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("questz").resolve("default");
    }

    private static List<Path> listChapterFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json")).sorted().toList();
        }
    }

    public static boolean hasDefaults() {
        try {
            return !listChapterFiles(getDefaultDirectory()).isEmpty();
        } catch (IOException exception) {
            return false;
        }
    }

    private static int copyDefaults(Path target) throws IOException {
        List<Path> defaults = listChapterFiles(getDefaultDirectory());
        Files.createDirectories(target);
        for (Path file : defaults) {
            Files.copy(file, target.resolve(file.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
        }
        return defaults.size();
    }

    @Nullable
    public static Path importDefaults(MinecraftServer server) {
        Path directory = getChapterDirectory(server);
        Path backup = server.getSavePath(WorldSavePath.ROOT).resolve("questz").resolve("backups").resolve(LocalDateTime.now().format(BACKUP_FORMAT));
        try {
            List<Path> current = listChapterFiles(directory);
            if (!current.isEmpty()) {
                Files.createDirectories(backup);
                for (Path file : current) {
                    Files.move(file, backup.resolve(file.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            int copied = copyDefaults(directory);
            QuestzMain.LOGGER.info("Imported {} default quest chapters from {}", copied, getDefaultDirectory());
            return current.isEmpty() ? directory : backup;
        } catch (IOException exception) {
            QuestzMain.LOGGER.error("Failed to import the default quest chapters", exception);
            return null;
        }
    }

    public static QuestBook load(MinecraftServer server) {
        Path directory = getChapterDirectory(server);
        if (!Files.exists(directory) && hasDefaults()) {
            try {
                int copied = copyDefaults(directory);
                QuestzMain.LOGGER.info("Installed {} default quest chapters from {}", copied, getDefaultDirectory());
            } catch (IOException exception) {
                QuestzMain.LOGGER.error("Failed to install the default quest chapters", exception);
            }
        }
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
