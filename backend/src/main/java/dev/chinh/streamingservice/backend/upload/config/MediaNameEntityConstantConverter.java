package dev.chinh.streamingservice.backend.upload.config;

import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class MediaNameEntityConstantConverter implements Converter<String, MediaNameEntityConstant> {

    @Override
    public MediaNameEntityConstant convert(@NotNull String source) {
        return MediaNameEntityConstant.fromValue(source);
    }
}

