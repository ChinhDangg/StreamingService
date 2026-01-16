package dev.chinh.streamingservice.mediaupload.validation;

import java.util.regex.Pattern;

public class FileSystemValidator {

    // Windows forbidden characters: < > : " / \ | ? *
    // Control characters (0-31) are also forbidden on Windows.
    private static final Pattern WINDOWS_FORBIDDEN_CHARS = Pattern.compile(".*[<>:\"/\\\\|?*\\x00-\\x1F].*");

    // Linux/macOS only technically forbid '/' and the Null byte
    private static final Pattern NIX_FORBIDDEN_CHARS = Pattern.compile(".*/.*");

    // Reserved Windows names (cannot be used as filenames)
    private static final String[] WINDOWS_RESERVED_NAMES = {
            "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5",
            "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4",
            "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    };

    public static String isValidPath(String path) {
        if (path == null || path.isBlank()) {
            return "Path must not be null or empty";
        }

        // Trim whitespace
        path = path.trim();

        // Normalize slashes
        path = path.replace("\\", "/");

        // Cannot start or end with '/'
        if (path.startsWith("/") || path.endsWith("/")) {
            return "Path must not start or end with '/";
        }

        // Split into segments
        String[] segments = path.split("/");

        for (String segment : segments) {
            if (segment.isBlank()) {
                return "Path must not contain empty segments";
            }

            String error = isValidName(segment);
            if (error != null) {
                return error;
            }
        }

        return null;
    }

    public static String isValidName(String name) {
        if (name == null || name.isEmpty()) {
            return "Name cannot be empty";
        }

        if (name.getBytes().length > 255) {
            return "Name cannot be larger than 255 bytes";
        }

        if (name.length() > 255) {
            return "Name cannot be longer than 255 characters";
        }

        // 1. STRICT BLOCK: Navigation links
        // These are reserved by the OS and cannot be "created"
        if (name.equals(".") || name.equals("..")) {
            return "Name cannot be '.' or '..'";
        }

        // 2. SECURITY BLOCK: Prevent climbing out of folders
        // Block names starting with "../" or "..\"
        if (name.startsWith("..") && (name.contains("/") || name.contains("\\"))) {
            return "Name cannot contain '..'";
        }

        // 3. ALLOWED: Hidden folders (Starting with single dot)
        // If the name is ".myfolder", this is perfectly fine for a normal user.
        if (name.startsWith(".") && name.length() > 1 && !name.startsWith("..")) {
            // This is a valid hidden folder but not allowing for this service
            return "Name cannot start with '.'";
        }

        // 2. Check for OS-specific illegal characters
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            return isValidWindowsName(name);
        } else {
            return isValidNixName(name);
        }
    }

    private static String isValidWindowsName(String name) {
        // Check for forbidden characters
        if (WINDOWS_FORBIDDEN_CHARS.matcher(name).matches()) {
            return "Name contains forbidden characters for Windows: < > : \" / \\ | ? *";
        }
        // Check for reserved system names (case-insensitive)
        for (String reserved : WINDOWS_RESERVED_NAMES) {
            if (name.equalsIgnoreCase(reserved)) {
                return "Name is reserved by the OS:";
            }
        }
        // Windows filenames cannot end with a space or a period
        if (name.endsWith(" ")|| name.endsWith(".")) {
            return "Name cannot end with a space or a period";
        }
        return null;
    }

    private static String isValidNixName(String name) {
        // Linux and macOS only forbid the forward slash
        if (NIX_FORBIDDEN_CHARS.matcher(name).matches()) {
            return "Name contains forbidden characters: /";
        }
        if (name.contains("\0")) {
            return "Name contains illegal null character";
        }
        return null;
    }
}
