package dev.chinh.streamingservice.filemanager.config.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OpenTelemetryLoggingConfig {

    @Value("${management.otlp.logging.endpoint}")
    private String logExporterUrl;

    @PostConstruct
    public void initOtlpLogging() {
        // Create the OTLP HTTP Exporter explicitly
        OtlpHttpLogRecordExporter logRecordExporter = OtlpHttpLogRecordExporter.builder()
                .setEndpoint(logExporterUrl)
                .build();

        // Create the Logger Provider and tag it with application name
        SdkLoggerProvider sdkLoggerProvider = SdkLoggerProvider.builder()
                .setResource(Resource.getDefault().merge(
                        Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "file-manager"))))
                // Process logs in batches, flushing every 2 seconds
                .addLogRecordProcessor(BatchLogRecordProcessor.builder(logRecordExporter)
                        .setScheduleDelay(Duration.ofSeconds(2))
                        .build())
                .build();

        // Create an isolated OpenTelemetry SDK dedicated solely to logging
        OpenTelemetrySdk logSdk = OpenTelemetrySdk.builder()
                .setLoggerProvider(sdkLoggerProvider)
                .build();

        // CRITICAL: Force Logback to use this specific SDK instead of the cached NoOp instance
        OpenTelemetryAppender.install(logSdk);
    }
}
