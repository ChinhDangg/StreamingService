package dev.chinh.streamingservice.filemanager.model;

import org.springframework.http.HttpStatus;

public record FileResponse<T>(
        boolean success,
        int code,
        T data,
        String message
) {

    public static <T> FileResponse<T> ok(T data) {
        return new FileResponse<>(true, HttpStatus.OK.value(), data, null);
    }

    public static <T> FileResponse<T> ok(T data, String message) {
        return new FileResponse<>(true, HttpStatus.OK.value(), data, message);
    }

    public static <T> FileResponse<T> error(int code, String message) {
        return new FileResponse<>(false, code, null, message);
    }
}
