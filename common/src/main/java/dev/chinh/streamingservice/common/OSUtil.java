package dev.chinh.streamingservice.common;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OSUtil {

    public enum OS {
        WINDOWS, MAC, LINUX, OTHER
    }

    private static final String BASE_DIR = "/chunks";
    private static final String CONTAINER = "nginx";

    private static OS currentOS;
    private static String RAMDISK = "nuLL";
    public static long MEMORY_TOTAL = 0;
    public static AtomicLong MEMORY_USABLE;

    public static void _init() {
        currentOS = _detectOS();
        RAMDISK = _getRAMDISKName();
    }

    private static OS _detectOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return OS.WINDOWS;
        } else if (osName.contains("mac")) {
            return OS.MAC;
        } else if (osName.contains("nux") || osName.contains("nix")) {
            return OS.LINUX;
        } else {
            return OS.OTHER;
        }
    }

    private static String _getRAMDISKName() {
        if (currentOS == OS.WINDOWS) {
            return "R:";
        } else if (currentOS == OS.MAC) {
            return "/Volumes/RAMDISK";
        } else if (currentOS == OS.LINUX) {
            return "/mnt/ramdisk";
        }
        throw new RuntimeException("Unsupported OS: " + currentOS);
    }

    public static void _createRamDisk(long ramBytes) throws Exception {
        if (currentOS != OS.LINUX && Files.exists(Paths.get(RAMDISK))) {
            System.out.println("Ramdisk already exists");
            return;
        }

        if (currentOS == OS.WINDOWS) {
            // skipping windows ramdisk as can't be mounted as volume
            return;
        }

        String[] command = switch (currentOS) {
            case OS.MAC -> new String[]{"/bin/bash", "-c",
//                    "diskutil erasevolume HFS+ 'RAMDISK' `hdiutil attach -nomount ram://1048576`"};
                    "diskutil erasevolume HFS+ 'RAMDISK' `hdiutil attach -nomount ram://" + (ramBytes / 512) + "`"};
            case OS.LINUX -> new String[]{
                    // chinh ALL=(root) NOPASSWD: /bin/mkdir, /bin/mount, /bin/umount
                    // "mkdir -p /mnt/ramdisk && mount -t tmpfs -o size=512m tmpfs /mnt/ramdisk"};
                    "/bin/bash", "-c",
                    """
                    if ! findmnt -n -o FSTYPE /mnt/ramdisk | grep -q 'tmpfs'; then
                        sudo mkdir -p /mnt/ramdisk && \
                        sudo mount -t tmpfs -o size=%d tmpfs /mnt/ramdisk;
                    fi
                    """.formatted(ramBytes)
            };
            case OS.WINDOWS -> new String[]{
                    "OSFMount.com",
                    "-a",          // add new disk
                    "-t", "vm",    // type: virtual memory (RAM)
                    "-s", "512M",  // size
                    "-m", "R:",    // mount point
                    "-o", "format:ntfs" // auto-format NTFS
            }; // to remove: OSFMount.com -d -m R:
            default -> throw new UnsupportedOperationException("Unsupported OS: " + currentOS);
        };

        try {
            runCommandAndLog(command, null);
        } catch (Exception e) {
            throw new Exception("Fail to create RAM DISK");
        }

        System.out.println("RAMDisk creation finished");
    }

    public static void _initializeRAMInfo() throws IOException, InterruptedException {
        MEMORY_TOTAL = getMemoryTotalSpace();
        MEMORY_USABLE = new AtomicLong(getActualMemoryUsableSpace());
    }

    public static void startDockerCompose() throws IOException, InterruptedException {
        String composeFile = switch (currentOS) {
            case OS.MAC -> "compose.mac.yaml";
            case OS.LINUX -> "compose.linux.yaml";
            case OS.WINDOWS -> "compose.windows.yaml";
            default -> "compose.yaml";
        };
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "compose", "-f", composeFile, "up", "-d"
        );
        pb.inheritIO();
        Process process = pb.start();
        int exit = process.waitFor();

        if (exit != 0) {
            throw new RuntimeException("docker compose failed with code " + exit);
        }
        System.out.println("docker compose finished with exit code: " + exit);
    }

    private static long getMemoryTotalSpace() throws IOException, InterruptedException {
        if  (currentOS == OS.WINDOWS) {
            return getMemoryTotalFromContainer();
        } else if (currentOS == OS.MAC || currentOS == OS.LINUX) {
            return getMemoryTotalFromRAMDisk();
        }
        throw new UnsupportedOperationException("Unsupported OS: " + currentOS);
    }

    private static long getMemoryTotalFromRAMDisk() throws IOException {
        FileStore store = Files.getFileStore(Paths.get(RAMDISK));
        return store.getTotalSpace();
    }

    private static long getMemoryTotalFromContainer() throws InterruptedException, IOException {
        Process process = new ProcessBuilder("docker", "exec", "nginx", "df", "-B1", "/chunks")
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor();

        String[] lines = output.split("\n");
        // [Filesystem      1B-blocks  Used  Available Use% Mounted on, tmpfs          1073741824     0 1073741824   0% /chunks]
        if (lines.length >= 2) {
            String[] parts = lines[1].trim().split("\\s+");
            return Long.parseLong(parts[1]);
        }
        throw new RuntimeException("Unable to get RAM total from container");
    }

    public static long getActualMemoryUsableSpace() throws IOException, InterruptedException {
        if (currentOS == OS.WINDOWS) {
            return getActualMemoryUsableSpaceFromContainer();
        }  else if (currentOS == OS.MAC || currentOS == OS.LINUX) {
            return getActualMemoryUsableSpaceFromRAMDisk();
        }
        throw new UnsupportedOperationException("Unsupported OS: " + currentOS);
    }

    private static long getActualMemoryUsableSpaceFromRAMDisk() throws IOException {
        FileStore store = Files.getFileStore(Paths.get(RAMDISK));
        return store.getUsableSpace();
    }

    private static long getActualMemoryUsableSpaceFromContainer() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("docker", "exec", "nginx", "df", "-B1", "/chunks");
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor();

        // Filesystem   1B-blocks      Used Available Use% Mounted on
        String[] lines = output.split("\n");
        if (lines.length >= 2) {
            String[] parts = lines[1].trim().split("\\s+");
            return Long.parseLong(parts[3]);
        }
        throw new RuntimeException("Unable to get RAM usable space from container");
    }

    public static long getUsableMemory() {
        return MEMORY_USABLE.get();
    }

    public static void updateUsableMemory(long used) {
        MEMORY_USABLE.addAndGet(used);
    }

    public static void refreshUsableMemory() throws IOException, InterruptedException {
        MEMORY_USABLE.set(getActualMemoryUsableSpace());
    }

    public static boolean deleteForceMemoryDirectory(String dir) throws IOException {
        if (currentOS == OS.WINDOWS) {
            return deleteForceDirectoryInContainer(dir);
        } else if (currentOS == OS.MAC || currentOS == OS.LINUX) {
            return deleteForceDirectoryForRAMDisk(dir);
        }
        throw new UnsupportedOperationException("Unsupported OS: " + currentOS);
    }

    private static boolean deleteForceDirectoryForRAMDisk(String pathString) throws IOException {
        pathString = normalizePath(RAMDISK, pathString);
        Path path = Paths.get(pathString);

        // 1. Check if it exists at all
        if (!Files.exists(path)) {
            return false;
        }

        // 2. Perform the deletion
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(@NonNull Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            Files.delete(path);
        }

        return true; // Something was deleted
    }

    private static boolean deleteForceDirectoryInContainer(String path) {
        path = normalizePath(BASE_DIR, path);

        // This shell script runs INSIDE the container:
        // 1. Check if path exists (-e)
        // 2. If yes, delete it recursively (-rf) and print 'deleted'
        // 3. If no, print 'not_found'
        String shellCommand = String.format(
                "if [ -e \"%s\" ]; then rm -rf \"%s\" && echo \"deleted\"; else echo \"not_found\"; fi",
                path, path
        );

        // wrap it in a shell (sh -c) for complex command like if else
        String[] cmd = {"docker", "exec", CONTAINER, "sh", "-c", shellCommand};

        try {
            // Assuming runCommandAndLog returns or logs the output stream
            String result = runCommandAndLog(cmd, null);

            if ("deleted".equals(result.trim())) {
                System.out.println("Success: Found and deleted " + path);
                return true;
            } else {
                System.out.println("Nothing to delete: " + path + " did not exist.");
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error: Failed to execute delete command in container.");
            return false;
        }
    }



    public static String readPlayListFromTempDir(String videoDir) throws IOException, InterruptedException {
        if  (currentOS == OS.WINDOWS) {
            return readPlaylistFromContainer(videoDir);
        } else if (currentOS == OS.MAC) {
            return readPlaylistFromRAMDisk(videoDir);
        }
        throw new UnsupportedOperationException("Unsupported OS: " + currentOS);
    }

    private static String readPlaylistFromRAMDisk(String videoDir) throws IOException {
        // Base directory for macOS RAMDISK mount — adjust if needed
        Path playlistPath = Paths.get(RAMDISK, videoDir, "master.m3u8");
        if (!Files.exists(playlistPath)) {
            return null;
        }

        // Efficient tail: only keep last 2 lines
        Deque<String> lastLines = new ArrayDeque<>(2);
        try (BufferedReader br = Files.newBufferedReader(playlistPath)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (lastLines.size() == 2) lastLines.removeFirst();
                lastLines.addLast(line);
            }
        }
        return String.join("\n", lastLines);
    }

    private static String readPlaylistFromContainer(String videoDir) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "docker", "exec", CONTAINER, "cat", "/chunks/" + videoDir + "/master.m3u8"
        ).redirectErrorStream(true).start();

        // Only keep the last 2 lines
        Deque<String> lastLines = new ArrayDeque<>(2);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (lastLines.size() == 2) lastLines.removeFirst(); // drop oldest
                lastLines.addLast(line);
            }
        }
        process.waitFor();
        if (lastLines.isEmpty()) {
            return null;
        }
        // Join the two last lines into a single string
        return String.join("\n", lastLines);
    }


    public static boolean checkTempFileExists(String fileName) {
        if (currentOS == OS.MAC || currentOS == OS.LINUX) {
            File playlist = new File(normalizePath(RAMDISK, fileName));
            return playlist.exists();
        }
        return containerFileExists(fileName);
    }

    private static boolean containerFileExists(String relativePath) {
        String targetPath = normalizePath(BASE_DIR, relativePath);

        String[] commands = {
                "docker", "exec", CONTAINER,
                "test", "-e", targetPath
        };

        try {
            runCommandAndLog(commands, null);
        } catch (Exception e) {
            return false;
        }
        return true; // 0 = exists, else doesn't
    }


    public static boolean writeTextToTempFile(String relativePath, List<String> lines, boolean createDir) throws Exception {
        int dot = relativePath.lastIndexOf('.');
        if (dot == -1 || dot == 0 || dot == relativePath.length() - 1) {
            System.out.println(("Invalid relative file path: " + relativePath));
            return false;
        }

        if (currentOS == OS.WINDOWS) {
            return writeTextToContainer(relativePath, lines, createDir);
        } else if (currentOS == OS.MAC || currentOS == OS.LINUX) {
            return writeTextToRAMDISK(relativePath, lines, createDir);
        }
        return false;
    }

    private static boolean writeTextToRAMDISK(String relativePath, List<String> lines, boolean createDir) throws Exception {
        String targetPath = normalizePath(RAMDISK, relativePath);
        if (createDir) {
            createTempDir(relativePath.substring(0, relativePath.lastIndexOf('/')));
            File concatList = new File(targetPath);
            try (PrintWriter pw = new PrintWriter(concatList)) {
                for (String part : lines) {
                    pw.println(part);
                }
            }
        } else {
            Path path = Paths.get(targetPath);
            if (!Files.exists(path)) {
                return false;
            }
            for (String part : lines) {
                Files.writeString(path, part + "\n", StandardOpenOption.APPEND);
            }
        }
        return true;
    }

    /**
     * Writes lines directly into a file inside the container's /chunks dir.
     * No host file is created (pure RAM inside container).
     * RelativePath must be file path and not a directory.
     */
    private static boolean writeTextToContainer(String relativePath, List<String> lines, boolean createDir) throws Exception {

        if (createDir) {
            String parentDir = relativePath.substring(0, relativePath.lastIndexOf('/'));
            createDirectoryInContainer(parentDir);
        }

        // Normalize target path inside /chunks
        String targetPath = normalizePath(BASE_DIR, relativePath);

        // Build docker exec command
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "exec", "-i", CONTAINER,
                "sh", "-c", "cat >> " + targetPath
        );

        Process process = pb.start();

        // Stream the content into container's stdin
        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream())) {
            for (String line : lines) {
                writer.write(line);
                writer.write("\n"); // System.lineSeparator()
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.out.println(exitCode);
            throw new IOException("Failed to write to container file: " + targetPath);
        }

        System.out.println("Wrote text directly to " + targetPath + " in " + CONTAINER);
        return true;
    }


    public static void createTempDir(String dir) throws Exception {
        if (currentOS == OS.MAC ||  currentOS == OS.LINUX) {
            createPathInRAMDisk(dir);
        } else {
            createDirectoryInContainer(dir);
        }
    }

    /**
     * Create a path inside Mac RAMDISK located at Volumes/RAMDISK/ since Mac RAMDISK can be mounted
     * as volume for docker. Making this write as simple as a normal file write.
     * Does not copy the content of the file. Only create the path if it doesn't exist.
     *
     * @param path The path that to write to RAMDISK
     * if the path is written, otherwise throw IOException
     */
    private static void createPathInRAMDisk(String path) throws IOException {
        File dir = new File(normalizePath(RAMDISK, path));
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Failed to create path: " + path);
            }
        }
    }

    /**
     * Create a directory directly inside the container under /chunks.
     */
    private static void createDirectoryInContainer(String relativeDir) throws Exception {
        String dirPath = normalizePath(BASE_DIR, relativeDir);

        try {
            runCommandAndLog(new String[]{"docker", "exec", CONTAINER, "mkdir", "-p", dirPath}, null);
        } catch (Exception e) {
            throw new IOException("Failed to create directory: " + dirPath, e);
        }
    }


    public static String normalizePath(String baseDir, String relativePath) {
        if (relativePath.startsWith("/")) {
            return baseDir + relativePath; // e.g. "/dir1" → "/chunks/dir1"
        }
        return baseDir + "/" + relativePath; // e.g. "dir1" → "/chunks/dir1"
    }

    public static String runCommandAndLog(String[] cmd, List<Integer> acceptableCode) throws Exception {
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(process.getInputStream().readAllBytes());
        int exit = process.waitFor();
        System.out.println("Command exited with code " + exit);
        if (exit != 0) {
            if (acceptableCode != null && acceptableCode.contains(exit))
                return out;
            throw new RuntimeException("Command failed with code " + exit + ": " + out);
        }
        return out;
    }
}
