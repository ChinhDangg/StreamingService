package dev.chinh.streamingservice.filemanager.exception;

import dev.chinh.streamingservice.filemanager.model.FileResponse;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<FileResponse<?>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("IllegalArgumentException: {}", e.getMessage());
        return ResponseEntity.badRequest().body(FileResponse.error(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<FileResponse<?>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("HttpRequestMethodNotSupportedException: {}", e.getMessage());
        return ResponseEntity.badRequest().body(FileResponse.error(HttpStatus.BAD_REQUEST.value(), "Not supported method"));
    }

    @ExceptionHandler(ConversionFailedException.class)
    public ResponseEntity<FileResponse<?>> handleConversionFailedException(ConversionFailedException e) {
        log.warn("ConversionFailedException: {}", e.getMessage());
        return ResponseEntity.badRequest().body(FileResponse.error(HttpStatus.BAD_REQUEST.value(), "Invalid parameter"));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<FileResponse<?>> handleValidationException(ValidationException e) {
        log.warn("ValidationException: {}", e.getMessage());
        return ResponseEntity.badRequest().body(FileResponse.error(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<FileResponse<?>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("MethodArgumentNotValidException: {}", e.getMessage());
        return ResponseEntity.badRequest().body(FileResponse.error(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public  ResponseEntity<FileResponse<?>> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        log.warn("HttpMediaTypeNotSupportedException: {}", e.getMessage());
        return ResponseEntity.badRequest().body(FileResponse.error(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<FileResponse<?>> handleException(Exception e) {
        log.error("Exception: {}", e.getMessage(), e);
        e.printStackTrace();
        return ResponseEntity.internalServerError().body(FileResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error"));
    }
}
