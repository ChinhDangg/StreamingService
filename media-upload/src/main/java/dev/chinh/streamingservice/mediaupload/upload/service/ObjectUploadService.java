package dev.chinh.streamingservice.mediaupload.upload.service;

import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ObjectUploadService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Retryable(
            retryFor = {
                    SdkClientException.class,  // network / transport
                    S3Exception.class          // transient S3 errors
            },
            maxAttempts = 5,
            backoff = @Backoff(delay = 1_000, multiplier = 2, maxDelay = 10_000)
    )
    public String getMultipartUploadId(String bucket, String objectName) {
        CreateMultipartUploadRequest multipartUploadRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(objectName)
                .build();

        try {
            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(multipartUploadRequest);
            return response.uploadId();
        } catch (S3Exception e) {
            if (e.statusCode() >= 500) {
                throw e; // retry
            }
            throw new IllegalStateException("Fatal S3 error", e);
        }
    }

    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 1_000, multiplier = 2, maxDelay = 10_000, random = true)
    )
    public String getPresignedPartUrl(String bucket, String objectName, String uploadId, int partNumber, Duration signatureDuration) {
        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(bucket)
                .key(objectName)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();

        PresignedUploadPartRequest presigned = s3Presigner.presignUploadPart(builder -> builder
                .signatureDuration(signatureDuration)
                .uploadPartRequest(uploadPartRequest)
        );
        return presigned.url().toString();
    }

    @Retryable(
            retryFor = {
                    SdkClientException.class,
                    S3Exception.class
            },
            maxAttempts = 5,
            backoff = @Backoff(delay = 1_000, multiplier = 2, maxDelay = 10_000, random = true)
    )
    public void completeMultipartUpload(String bucket, String objectName, String uploadId, List<CompletedPart> completedParts) {
        CompletedMultipartUpload multipartUploadUpload = CompletedMultipartUpload.builder()
                .parts(completedParts)
                .build();

        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(objectName)
                .uploadId(uploadId)
                .multipartUpload(multipartUploadUpload)
                .build();
        try {
            s3Client.completeMultipartUpload(completeRequest);
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            // Retry ONLY on transient server-side errors
            if (e.statusCode() >= 500) {
                throw e; // retry
            }

            // Explicitly do NOT retry these (they need a different action)
            // - NoSuchUpload => restart multipart
            // - InvalidPart / InvalidRequest => bug or wrong ETags/part list
            throw new IllegalStateException("Fatal S3 error completing multipart upload", e);
        }
    }
}
