package dev.chinh.streamingservice.workers.service;

import dev.chinh.streamingservice.common.data.MediaJobDescription;

public interface JobHandler {

    void handleJob(String tokenKey, MediaJobDescription jobDescription);
}
