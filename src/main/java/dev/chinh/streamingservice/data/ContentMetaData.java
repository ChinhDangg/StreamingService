package dev.chinh.streamingservice.data;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;

public class ContentMetaData {

    // Fields to use to ensure correct naming
    // Search
    public static final String TITLE = "title";
    public static final String TAGS = "tags";
    public static final String CHARACTERS = "characters";
    public static final String UNIVERSES = "universes";
    public static final String AUTHORS = "authors";
    public static final String UPLOAD_DATE = "uploadDate";
    public static final String YEAR = "year";
    public static final String WIDTH = "width";
    public static final String HEIGHT = "height";
    // Classification
    public static final String ID = "id";
    public static final String BUCKET = "bucket";
    public static final String PARENT_PATH = "parentPath";
    public static final String KEY = "key";
    public static final String THUMBNAIL = "thumbnail";
    public static final String LENGTH = "length";

    // Technical
    private short frameRate;
    private String format;
    private String absoluteFilePath;

    // Grouping (optional so maybe another table)
    private String groupTitle;      // e.g.: the walking dead
    private int groupId;
    private int groupOrder;         // 1 (season 1)
    private String seriesTitle;     // the walking dead season 1
    private int chapter;            // 1 (episode 1)

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
