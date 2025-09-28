package dev.chinh.streamingservice;

import lombok.Getter;

@Getter
public enum Resolution {
    p1080(1080),
    p720(720),
    p360(360);

    private final int resolution;

    Resolution(int resolution) {
        this.resolution = resolution;
    }
}
