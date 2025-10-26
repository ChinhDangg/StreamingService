package dev.chinh.streamingservice.search.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MediaSearchRangeField {

    private String field;
    private Object fromValue;
    private Object toValue;
}
