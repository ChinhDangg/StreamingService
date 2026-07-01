package dev.chinh.taskresponder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisJobStatusListener {

    private final SimpMessagingTemplate simpleMessagingTemplate;
    private final ObjectMapper objectMapper;

    public record JobStatus(String jobId, String userId, Object result) {}

    // registered as a listener in RedisConfig
    public void handleJob(String message) {
        JobStatus jobStatus;
        try {
            jobStatus = objectMapper.readValue(message, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            System.err.println(e.getMessage());
            System.out.println("Failed to parse job status message: " + message);
            return;
        }

        System.out.println(jobStatus);

        simpleMessagingTemplate.convertAndSendToUser(
                jobStatus.userId,
                "/queue/job-reply",
                jobStatus
        );
    }
}
