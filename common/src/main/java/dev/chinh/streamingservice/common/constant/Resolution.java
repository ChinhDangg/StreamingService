package dev.chinh.streamingservice.common.constant;

import lombok.Getter;

@Getter
public enum Resolution {
    original(99999),  // big number to supersede other resolutions (prevent re-encoding)
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

    public static long getEstimatedSize(long size, int width, int height, Resolution target) {
        double percent = (getSecondDimension(target) * target.getResolution()) / (double) (width * height);
        percent += percent * 0.1;
        return (long) (size * percent);
    }

    public static int getSecondDimension(Resolution resolution) {
        if (resolution == Resolution.p2160)
            return 3860;
        else if (resolution == Resolution.p1440)
            return 2560;
        else if (resolution == Resolution.p1080)
            return 1920;
        else if (resolution == Resolution.p720)
            return 1280;
        else if (resolution == Resolution.p480)
            return 854;
        else if (resolution == Resolution.p360)
            return 640;
        else if (resolution == Resolution.p240)
            return 426;
        return -1;
    }
}
