package dev.chinh.streamingservice.workers.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MemoryManager {

    private final List<ResourceCleanable> cleanableList = new ArrayList<>();

    public void registerResourceCleanable(ResourceCleanable cleanable) {
        cleanableList.add(cleanable);
    }

    public boolean freeMemoryForSize(long size) {
        for (ResourceCleanable cleanable : cleanableList) {
            if (cleanable.freeMemorySpaceForSize(size)) {
                return true;
            }
        }
        return false;
    }
}
