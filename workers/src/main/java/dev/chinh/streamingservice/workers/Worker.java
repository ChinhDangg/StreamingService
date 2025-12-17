package dev.chinh.streamingservice.workers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.service.WorkerRedisService;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
        }
    }

    private void handleRecord(MapRecord<String, Object, Object> record) {
        Map<Object, Object> fields = record.getValue();

        int retry = Integer.parseInt(fields.getOrDefault("retry", 0).toString());
        if (retry >= MAX_RETRIES) {
            sendToDLQ(record, "max retries reached");
            workerRedisService.ack(streamKey(), groupName(), record.getId());
            return;
        }

        String jobJson = (String) fields.get(jobDescriptionKey);

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
            return;
        }

        try {
            performJob(mediaJobDescription);
            workerRedisService.ack(streamKey(), groupName(), record.getId());
        } catch (Exception e) {
            workerRedisService.updateStatus(mediaJobDescription.getWorkId(), MediaJobStatus.FAILED.name());
            // ack old one but readd with incremented retry
            workerRedisService.ack(streamKey(), groupName(), record.getId());
            incrementRetry(streamKey(), record.getId(), retry);
            backoff(retry);
            e.printStackTrace();
        }
        // release token is handled by the job caller not worker to handle async jobs
    }

    // Redis Streams are append-only — add metadata and don’t mutate.
    private void incrementRetry(String stream, RecordId id, int retry) {
        queueRedisTemplate.opsForStream().add(
                StreamRecords.mapBacked(
                        Map.of("retry", retry + 1)
                ).withStreamKey(stream)
        );
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

        if (!pending.iterator().hasNext()) {
            return;
        }

        // 2. Select stale jobs (idle > 60s)
        List<RecordId> staleIds = new ArrayList<>();
        for (PendingMessage p : pending) {
            Duration idle = p.getElapsedTimeSinceLastDelivery();
            if (idle.toMillis() > 60_000) {
                staleIds.add(p.getId());
            }
        }

        if (staleIds.isEmpty()) {
            return;
        }

        // 3. Claim them for THIS consumer
        workerRedisService.claim(streamKey(), groupName(), consumerName, Duration.ofSeconds(60), staleIds);
    }
}
