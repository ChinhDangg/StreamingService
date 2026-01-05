package dev.chinh.streamingservice.workers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.workers.service.WorkerRedisService;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public abstract class Worker implements Runnable {

    protected final WorkerRedisService workerRedisService;
    protected final RedisTemplate<String, String> queueRedisTemplate;
    protected final ObjectMapper objectMapper;

    protected abstract String streamKey();
    protected abstract String groupName();
    protected abstract String tokenKey();
    protected abstract String streamKeyDLQ();

    private final String jobDescriptionKey = "job_description";
    private final String consumerName = getClass().getSimpleName() + "-" + UUID.randomUUID();
    private static final int MAX_RETRIES = 5;

    @Override
    public void run() {
        workerRedisService.createGroupIfAbsent(streamKey(), groupName());

        reclaimStaleJobs();

        while (true) {
            try {
                List<MapRecord<String, Object, Object>> records = workerRedisService.readFromStream(
                        streamKey(),
                        groupName(),
                        consumerName,
                        Duration.ofSeconds(5)
                );

                if (records == null || records.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, Object, Object> record : records) {
                    handleRecord(record);
                }
            } catch (Exception ignored) {}
        }
    }

    private void handleRecord(MapRecord<String, Object, Object> record) {
        Map<Object, Object> fields = record.getValue();

        String workId = record.getId().getValue();

        int retry = getRetryCount(workId);

        if (retry >= MAX_RETRIES) {
            sendToDLQ(record, "max retries reached");
            workerRedisService.ack(streamKey(), groupName(), record.getId());
            clearRetry(workId);
            return;
        }

        String jobJson = (String) fields.get(jobDescriptionKey);

        System.out.println("JobJson: " + jobJson);

        MediaJobDescription mediaJobDescription;
        try {
            mediaJobDescription = objectMapper.readValue(jobJson, MediaJobDescription.class);
        } catch (JsonProcessingException e) {
            // Poison pill - ACK and drop
            workerRedisService.ack(streamKey(), groupName(), record.getId());
            System.out.println("Failed to parse job description: " + jobJson);
            return;
        }

        if (!workerRedisService.tryAcquireToken(tokenKey())) {
            sleep(500);
        }

        try {
            System.out.println("Performing job: " + mediaJobDescription.getWorkId() + " " + mediaJobDescription.getJobType());
            performJob(mediaJobDescription);
            workerRedisService.ack(streamKey(), groupName(), record.getId());
            clearRetry(workId);
        } catch (Exception e) {
            workerRedisService.updateStatus(mediaJobDescription.getWorkId(), MediaJobStatus.FAILED.name());
            incrementRetry(workId);
            backoff(retry);
            e.printStackTrace();
        }
        // release token is handled by the job caller not worker to handle async jobs
    }

    public int getRetryCount(String workId) {
        Object value = queueRedisTemplate.opsForHash().get("retry:"+workId, "count");
        return value == null ? 0 : Integer.parseInt((String) value);
    }

    public void incrementRetry(String workId) {
        Object value = queueRedisTemplate.opsForHash().get("retry:" + workId, "count");

        int currentCount = (value == null) ? 0 : Integer.parseInt((String) value);
        currentCount++;

        queueRedisTemplate.opsForHash().put("retry:" + workId, "count", String.valueOf(currentCount));
    }


    public void clearRetry(String workId) {
        queueRedisTemplate.delete("retry:"+workId);
    }


    private void backoff(int retry) {
        long delay = Math.min(1000L * (1L << retry), 30000);
        sleep(delay);
    }

    // Dead-Letter Queue
    // Debug failures
    // Avoid infinite retries
    // Preserve bad inputs
    private void sendToDLQ(MapRecord<String, Object, Object> record, String reason) {
        queueRedisTemplate.opsForStream().add(
                StreamRecords.mapBacked(
                        Map.of(
                                jobDescriptionKey, record.getValue().get(jobDescriptionKey),
                                "reason", reason,
                                "failed_at", Instant.now().toString()
                        )
                ).withStreamKey(streamKeyDLQ())
        );
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    public abstract void performJob(MediaJobDescription mediaJobDescription) throws Exception;

    // claiming failed to ack job
    private void reclaimStaleJobs() {
        // 1. Fetch pending messages (with details)
        PendingMessages pending = workerRedisService.getPendingMessages(
                streamKey(),
                groupName(),
                50
        );

        List<RecordId> stale = pending.stream()
                .filter(p -> p.getElapsedTimeSinceLastDelivery().toMillis() > 60000)
                .map(PendingMessage::getId)
                .toList();

        if (stale.isEmpty()) return;

        List<MapRecord<String,Object,Object>> claimed =
                queueRedisTemplate.opsForStream().claim(
                        streamKey(),
                        groupName(),
                        consumerName,
                        Duration.ofSeconds(60),
                        stale.toArray(new RecordId[0])
                );

        // Re-handle claimed jobs normally
        for (MapRecord<String,Object,Object> record : claimed) {
            handleRecord(record);
        }
    }
}
