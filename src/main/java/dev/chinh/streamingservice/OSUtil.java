package dev.chinh.streamingservice;

import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
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

    public OSUtil() throws Exception {
        currentOS = detectOS();
        RAMDISK = getRAMDISKName();
        if (!OSUtil.createRamDisk()) {
            throw new Exception("Fail to create RAM DISK");
        }
    }

    public static OS detectOS() {
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

    public static void initializeRAMInfo() throws IOException, InterruptedException {
        MEMORY_TOTAL = getMemoryTotalSpace();
        MEMORY_USABLE = new AtomicLong(getActualMemoryUsableSpace());
    }

    private String getRAMDISKName() {
        if (currentOS == OS.WINDOWS) {
            return "R:";
        } else if (currentOS == OS.MAC) {
            return "/Volumes/RAMDISK";
        } else if (currentOS == OS.LINUX) {
            return "/mnt/ramdisk";
        }
        throw new RuntimeException("Unsupported OS: " + currentOS);
    }

    public static boolean createRamDisk() throws Exception {
        if (Files.exists(Paths.get(RAMDISK))) {
            System.out.println("Ramdisk already exists");
            return true;
        }

        String[] command = switch (currentOS) {
            case OS.MAC -> new String[]{"/bin/bash", "-c",
                    "diskutil erasevolume HFS+ 'RAMDISK' `hdiutil attach -nomount ram://1048576`"};
            case OS.LINUX -> new String[]{"/bin/bash", "-c",
                    "mkdir -p /mnt/ramdisk && mount -t tmpfs -o size=512m tmpfs /mnt/ramdisk"};
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

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[ramdisk] " + line);
            }
        }

        int exitCode = process.waitFor();
        System.out.println("RAMDisk creation finished with exit code: " + exitCode);
        return exitCode == 0;
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

    public static void main(String[] args) throws IOException, InterruptedException {
    }

    public static boolean createTempDir(String dir) throws IOException, InterruptedException {
        if (currentOS == OS.MAC) {
            return createPathInRAMDisk(dir);
        }
        return createDirectoryInContainer(dir);
    }

    public static boolean checkTempFileExists(String fileName) throws IOException, InterruptedException {
        if (currentOS == OS.MAC) {
            File playlist = new File(normalizePath(RAMDISK, fileName));
            return playlist.exists();
        }
        return containerFileExists(fileName);
    }

    public static boolean createTempTextFile(String relativePath, List<String> lines) throws IOException, InterruptedException {
        if (!relativePath.substring(relativePath.lastIndexOf(".")).equalsIgnoreCase(".txt")) {
            return false;
        }
        if (currentOS == OS.WINDOWS) {
            return writeTextToContainer(relativePath, lines);
        } else if (currentOS == OS.MAC) {
            String targetPath = normalizePath(RAMDISK, relativePath);
            File concatList = new File(targetPath);
            try (PrintWriter pw = new PrintWriter(concatList)) {
                for (String part : lines) {
                    pw.println(part);
                }
            }
            return true;
        }
        return false;
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

    public static void deleteForceMemoryDirectory(String dir) throws IOException, InterruptedException {
        if (currentOS == OS.WINDOWS) {
            deleteForceDirectoryInContainer(dir);
            return;
        } else if (currentOS == OS.MAC || currentOS == OS.LINUX) {
            deleteForceDirectoryForRAMDisk(dir);
            return;
        }
        throw new UnsupportedOperationException("Unsupported OS: " + currentOS);
    }

    private static void deleteForceDirectoryForRAMDisk(String dir) {
        try {
            dir = normalizePath(RAMDISK, dir);
            if (!Files.exists(Paths.get(dir))) {
                System.out.println("RAM disk does not exist to delete: " + dir);
                return;
            }

            String[] cmd = new String[]{"rm", "-rf", dir};
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            int code = process.waitFor();

            if (code != 0) {
                String out = new String(process.getInputStream().readAllBytes());
                System.err.println("Deletion failed for " + dir + ": " + out);
            } else {
                System.out.println("RAM disk successfully deleted: " + dir);
            }
        } catch (Exception e) {
            System.err.println("Error deleting " + dir + ": " + e.getMessage());
        }
    }

    private static void deleteForceDirectoryInContainer(String path) throws IOException, InterruptedException {
        path = normalizePath(BASE_DIR, path);
        String[] cmd = {"docker", "exec", CONTAINER, "rm", "-rf", path};
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        String output = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();

        if (code != 0) {
            System.err.println("Failed to delete in container: " + output);
        } else {
            System.out.println("Deleted " + path + " in container");
        }
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

    /**
     * Create a path inside Mac RAMDISK located at Volumes/RAMDISK/ since Mac RAMDISK can be mounted
     * as volume for docker. Making this write as simple as a normal file write.
     * Does not copy the content of the file. Only create the path if it doesn't exist.
     *
     * @param path The path that to write to RAMDISK
     * @return true, if the path is written, otherwise throw IOException
     */
    private static boolean createPathInRAMDisk(String path) throws IOException {
        File dir = new File(normalizePath(RAMDISK, path));
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Failed to create path: " + path);
            }
        }
        return true;
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

    private static boolean containerFileExists(String relativePath) throws IOException, InterruptedException {
        String targetPath = normalizePath(BASE_DIR, relativePath);

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "exec", CONTAINER,
                "test", "-f", targetPath
        );

        Process process = pb.start();
        int exitCode = process.waitFor();

        return exitCode == 0; // 0 = exists, else doesn't
    }

    /**
     * Create a directory directly inside the container under /chunks.
     */
    private static boolean createDirectoryInContainer(String relativeDir) throws IOException, InterruptedException {
        String dirPath = normalizePath(BASE_DIR, relativeDir);

        return runCommand(List.of("docker", "exec", CONTAINER, "mkdir", "-p", dirPath),
                "Created directory in container: " + dirPath);
    }

    public static String normalizePath(String baseDir, String relativePath) {
        if (relativePath.startsWith("/")) {
            return baseDir + relativePath; // e.g. "/dir1" → "/chunks/dir1"
        }
        return baseDir + "/" + relativePath; // e.g. "dir1" → "/chunks/dir1"
    }

    private static boolean runCommand(List<String> command, String successMsg)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO(); // print stdout/stderr to console
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("Command failed: " + String.join(" ", command));
        }

        System.out.println("✅ " + successMsg);
        return true;
    }

    /**
     * Writes lines directly into a file inside the container's /chunks dir.
     * No host file is created (pure RAM inside container).
     */
    private static boolean writeTextToContainer(String relativePath, List<String> lines)
            throws IOException, InterruptedException {

        // Normalize target path inside /chunks
        String targetPath = normalizePath(BASE_DIR, relativePath);

        String parentDir = targetPath.substring(0, targetPath.lastIndexOf('/'));
        if (!parentDir.equals(BASE_DIR)) {
            // Strip the /chunks/ prefix to pass a relative path to createDirectoryInContainer
            String relativeParent = parentDir.substring(BASE_DIR.length() + 1);
            createDirectoryInContainer(relativeParent);
        }

        // Build docker exec command
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "exec", "-i", CONTAINER,
                "sh", "-c", "cat > " + targetPath
        );

        Process process = pb.start();

        // Stream the content into container's stdin
        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream())) {
            for (String line : lines) {
                writer.write(line);
                writer.write(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.out.println(exitCode);
            throw new IOException("Failed to write to container file: " + targetPath);
        }

        System.out.println("✅ Wrote text directly to " + targetPath + " in " + CONTAINER);
        return true;
    }
}
