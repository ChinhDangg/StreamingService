package dev.chinh.streamingservice.mediaupload.upload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.common.OSUtil;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final RedisTemplate<String, String> redisStringTemplate;
    private final ObjectMapper objectMapper;
    private final MinIOService minIOService;

    public String generateThumbnailFromVideo(String bucket, String objectName) {
        String thumbnailObject = objectName.substring(0, objectName.lastIndexOf(".")) + "-thumb.jpg";
        thumbnailObject = thumbnailObject.startsWith(MediaUploadService.defaultVidPath)
                ? thumbnailObject
                : OSUtil.normalizePath(MediaUploadService.defaultVidPath, thumbnailObject);

        MediaJobDescription jobDescription = new MediaJobDescription();
        jobDescription.setBucket(bucket);
        jobDescription.setPath(objectName);
        jobDescription.setWorkId(thumbnailObject);
        jobDescription.setJobType("videoThumbnail");

        try {
            addJobToQueue(ContentMetaData.FFMPEG_VIDEO_QUEUE_KEY, jobDescription);
        } catch (JsonProcessingException e) {
            System.err.println("Failed to generate thumbnail for video " + objectName);
        }

        return thumbnailObject;
    }

    public void addJobToQueue(String queueKey, MediaJobDescription mediaJobDescription) throws JsonProcessingException {
        System.out.println("Adding job to queue: " + mediaJobDescription.getWorkId());
        redisStringTemplate.opsForStream().add(
                StreamRecords.string(
                        Collections.singletonMap("job_description", objectMapper.writeValueAsString(mediaJobDescription))
                ).withStreamKey(queueKey));
    }

    public void deleteMediaThumbnail(String objectName) throws Exception {
        minIOService.removeFile(ContentMetaData.THUMBNAIL_BUCKET, objectName);
    }
}
