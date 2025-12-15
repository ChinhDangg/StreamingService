package dev.chinh.streamingservice.workers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.streamingservice.service.WorkerRedisService;
import dev.chinh.streamingservice.common.constant.MediaJobStatus;
import dev.chinh.streamingservice.common.data.MediaJobDescription;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.MapRecord;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public abstract class Worker implements Runnable {

    protected final WorkerRedisService workerRedisService;
    protected final ObjectMapper objectMapper;

    protected abstract String streamKey();
    protected abstract String groupName();
    protected abstract String tokenKey();

    private final String consumerName = getClass().getSimpleName() + "-" + UUID.randomUUID();

    @Override
    public void run() {
        workerRedisService.createGroupIfAbsent(streamKey(), groupName());

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
        String jobJson = (String) record.getValue().get("job_description");

        MediaJobDescription mediaJobDescription;
        try {
            mediaJobDescription = objectMapper.readValue(jobJson, MediaJobDescription.class);
        } catch (JsonProcessingException e) {
            // Poison pill - ACK and drop
            workerRedisService.ack(streamKey(), groupName(), record.getId());
            return;
        }

        if (!workerRedisService.tryAcquireToken(tokenKey())) {
            sleep(500);
            return;
        }

        try {
            workerRedisService.updateStatus(mediaJobDescription.getWorkId(), MediaJobStatus.RUNNING.name());
            performJob(mediaJobDescription);
            workerRedisService.updateStatus(mediaJobDescription.getWorkId(), MediaJobStatus.COMPLETED.name());
            workerRedisService.ack(streamKey(), groupName(), record.getId());
        } catch (Exception e) {
            workerRedisService.updateStatus(mediaJobDescription.getWorkId(), MediaJobStatus.FAILED.name());
            // NO ACK stays in PEL for retry
            e.printStackTrace();
        } finally {
            workerRedisService.releaseToken(tokenKey());
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    public abstract void performJob(MediaJobDescription mediaJobDescription) throws Exception;
}
