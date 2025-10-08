package dev.chinh.streamingservice.data.entity;

public interface MediaDescriptor {

    String getPath();
    String getBucket();
    Integer getLength();
    Integer getWidth();
    Integer getHeight();
    String getThumbnail();
    boolean hasKey();
    boolean hasThumbnail();
}
