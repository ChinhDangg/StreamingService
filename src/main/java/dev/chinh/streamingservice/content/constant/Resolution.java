package dev.chinh.streamingservice.content.constant;

import lombok.Getter;

@Getter
public enum Resolution {
    original(-1),
    p2160(2160),
    p1440(1440),
    p1080(1080),
    p720(720),
    p480(480),
    p360(360),
    p240(240);

    private final int resolution;

    Resolution(int resolution) {
        this.resolution = resolution;
    }
}
