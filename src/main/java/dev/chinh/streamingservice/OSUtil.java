package dev.chinh.streamingservice;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;

@Component
public class OSUtil {

    public enum OS {
        WINDOWS, MAC, LINUX, OTHER
    }

    private static OS currentOS;

    @PostConstruct
    public void init() {
        currentOS = getOS();
    }

    public static OS getOS() {
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

    private static final String MAC_RAMDISK = "Volumes/RAMDISK/";

    public static boolean createTempDir(String dir) throws IOException, InterruptedException {
        if (currentOS == OS.WINDOWS) {
            return createDirectoryInContainer(dir);
        } else if (currentOS == OS.MAC) {
            return createPathInRAMDisk(dir);
        }
        return false;
    }

    public static boolean createTempTextFile(String relativePath, List<String> lines) throws IOException, InterruptedException {
        if (!relativePath.substring(relativePath.lastIndexOf(".")).equalsIgnoreCase(".txt")) {
            return false;
        }
        if (currentOS == OS.WINDOWS) {
            return writeTextToContainer(relativePath, lines);
        } else if (currentOS == OS.MAC) {
            String targetPath = relativePath.startsWith("/")
                    ? MAC_RAMDISK + relativePath
                    : MAC_RAMDISK + "/" + relativePath;
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
        File dir = new File(MAC_RAMDISK + path);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Failed to create path: " + path);
            }
        }
        return true;
    }

    /**
     * Create a directory directly inside the container under /chunks.
     */
    private static boolean createDirectoryInContainer(String relativeDir) throws IOException, InterruptedException {
        String dirPath = normalizePath(relativeDir);

        return runCommand(List.of("docker", "exec", CONTAINER, "mkdir", "-p", dirPath),
                "Created directory in container: " + dirPath);
    }

    private static String normalizePath(String relativePath) {
        if (relativePath.startsWith("/")) {
            return BASE_DIR + relativePath; // e.g. "/dir1" → "/chunks/dir1"
        }
        return BASE_DIR + "/" + relativePath; // e.g. "dir1" → "/chunks/dir1"
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
        String targetPath = normalizePath(relativePath);

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
