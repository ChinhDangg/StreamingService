package dev.chinh.streamingservice;

import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Component
public class OSUtil {

    public enum OS {
        WINDOWS, MAC, LINUX, OTHER
    }

    private static OS currentOS;

    public OSUtil() {
        currentOS = detectOS();
        RAMDISK = getRAMDISKName();
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

    private static final String BASE_DIR = "/chunks";
    private static final String CONTAINER = "nginx";

    private static String RAMDISK = "t";

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

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println(checkTempFileExists("2b.mp4/preview/master.m3u8"));
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

    private static String normalizePath(String baseDir, String relativePath) {
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
