package dev.chinh.streamingservice.search.data;

public record NameEntityField(
        long id,
        long userId,
        String name,
        int length,
        String thumbnail
) {
}
