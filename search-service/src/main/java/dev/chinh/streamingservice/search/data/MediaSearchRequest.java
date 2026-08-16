package dev.chinh.streamingservice.search.data;

import lombok.*;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MediaSearchRequest {

    List<MediaSearchField> includeFields;
    List<MediaSearchField> excludeFields;
    List<MediaSearchRangeField> rangeFields;

    public boolean hasAny() {
        return (includeFields != null && !includeFields.isEmpty() && includeHasAny()) ||
                (excludeFields != null && !excludeFields.isEmpty() && excludeHasAny()) ||
                (rangeFields != null && !rangeFields.isEmpty() && rangeHasAny());
    }

    private boolean includeHasAny() {
        for (MediaSearchField field : includeFields) {
            if (field.hasAny())
                return true;
        }
        return false;
    }

    private boolean excludeHasAny() {
        for (MediaSearchField field : excludeFields) {
            if (field.hasAny())
                return true;
        }
        return false;
    }

    private boolean rangeHasAny() {
        for (MediaSearchRangeField field : rangeFields) {
            if (field.hasAny())
                return true;
        }
        return false;
    }
}
