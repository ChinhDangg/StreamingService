package dev.chinh.streamingservice.data;

import dev.chinh.streamingservice.content.constant.Resolution;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.util.Arrays;

public class MediaMetaData {

    private String id;
    private String bucket;
    private String parentPath;
    private String key;             // if key exist then is an individual content, otherwise use parentPath for grouping
    private String thumbnail;
    private String title;
    private int total;

    // Classification
    private String[] tags;
    private String[] characters;
    private String[] universes;
    private String[] authors;

    // Technical
    private Resolution resolution;
    private short frameRate;
    private String format;
    private int length;
    private LocalDate uploadDate;
    private String absoluteFilePath;

    // Grouping
    private String groupTitle;      // e.g.: the walking dead
    private int groupId;
    private int groupOrder;         // 1 (season 1)
    private String seriesTitle;     // the walking dead season 1
    private int chapter;            // 1 (episode 1)

    public String getBucketAbsolutePath() {
        return bucket + "/" + parentPath + "/" + key;
    }

    public String getTopParentPath() {
        return bucket + "/" + parentPath;
    }

    public boolean hasGroup() {
        return groupTitle != null;
    }

    public boolean hasSeries() {
        return seriesTitle != null;
    }


    public static void main(String[] args) throws IOException {
//        File[] mainFolders = getMainFolders("E:\\Readonly");
//        for (File folder : mainFolders) {
//            System.out.println(folder.getName());
//        }

        //walkAndGetDirThatContainsFile(new File("E:\\Temp"));
        walkAndStopIfHasFile(new File("E:\\Readonly\\3D"));
    }

    public static File[] getMainFolders(String folderPath) {
        File folder = new File(folderPath);

        if (folder.exists() && folder.isDirectory()) {
            // List only directories
            File[] subFolders = folder.listFiles(File::isDirectory);

            if (subFolders != null) {
                // Sort by name
                Arrays.sort(subFolders, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
                return subFolders;
            } else {
                System.out.println("No subfolders found.");
            }
        } else {
            System.out.println("The specified path is not a folder or does not exist.");
        }
        return new File[]{};
    }

    public static int count = 0;
    public static void walkAndGetDirThatContainsFile(File mainFolder) throws IOException {
        Files.walkFileTree(mainFolder.toPath(), new SimpleFileVisitor<>() {

            @NotNull @Override
            public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    boolean hasFile = false;
                    for (Path entry : stream) {
                        if (Files.isRegularFile(entry)) {
                            hasFile = true;
                            break;
                        }
                    }
                    if (hasFile) {
                        System.out.println(count + ": " + dir); // print directory path once
                        count++;
                    }
                }
                return FileVisitResult.CONTINUE; // keep going inside
            }
        });
    }

    public static void walkAndStopIfHasFile(File mainFolder) throws IOException {
        Files.walkFileTree(mainFolder.toPath(), new SimpleFileVisitor<>() {
            @Override @NotNull
            public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    boolean hasFile = false;
                    for (Path entry : stream) {
                        if (Files.isRegularFile(entry)) {
                            hasFile = true;
                            System.out.println(entry.getFileName());
                        }
                    }

                    if (hasFile) {
                        System.out.println(count + ": " + dir); // print directory path once
                        count++;
                        // Directory has a file → don't go inside subfolders
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                // Directory has no files → keep going inside
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
