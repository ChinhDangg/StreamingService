package dev.chinh.streamingservice.workers.service;

public interface ResourceCleanable {

    boolean freeMemorySpaceForSize(long size);
}
