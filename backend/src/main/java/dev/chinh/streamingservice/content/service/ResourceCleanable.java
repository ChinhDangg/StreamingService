package dev.chinh.streamingservice.content.service;

public interface ResourceCleanable {

    boolean freeMemorySpaceForSize(long size);
}
