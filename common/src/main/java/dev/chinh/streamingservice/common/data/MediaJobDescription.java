package dev.chinh.streamingservice.common.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.common.constant.Resolution;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class MediaJobDescription {

    private String workId;
    private String jobType;
    private Instant scheduledTime = Instant.now();
    private Resolution resolution;
    private String acceptHeader;
    private int offset;
    private int batch;
    private int vidNum;
    private Resolution vidResolution;

    @JsonProperty(ContentMetaData.ID)
    private long id;
    @JsonProperty(ContentMetaData.BUCKET)
    private String bucket;
    private String path;
    @JsonProperty(ContentMetaData.LENGTH)
    private int length;
    @JsonProperty(ContentMetaData.WIDTH)
    private int width;
    @JsonProperty(ContentMetaData.HEIGHT)
    private int height;
    @JsonProperty(ContentMetaData.SIZE)
    private long size;
    @JsonProperty(ContentMetaData.PREVIEW)
    private String preview;
}

