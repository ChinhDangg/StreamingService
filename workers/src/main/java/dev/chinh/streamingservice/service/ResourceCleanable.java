package dev.chinh.streamingservice.service;

public interface ResourceCleanable {

    boolean freeMemorySpaceForSize(long size);
}
