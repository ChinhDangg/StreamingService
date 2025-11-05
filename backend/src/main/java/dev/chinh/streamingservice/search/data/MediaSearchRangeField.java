package dev.chinh.streamingservice.search.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MediaSearchRangeField {

    private String field;
    private Object from;
    private Object to;

    public boolean hasAny() {
        return field != null && !field.isBlank() && (from != null || to != null);
    }
}
