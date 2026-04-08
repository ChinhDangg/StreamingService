package dev.chinh.streamingservice.filemanager.constant;

import lombok.Getter;

@Getter
public enum FileStatus {
    DELETING((short) -1),
    PROCESSING((short) -2),
    IN_USE((short) -3),
    BEING_MOVED_INTO((short) -4),
    BEING_MOVED((short) -5);

    private final short value;

    FileStatus(short value) {
        this.value = value;
    }
}
