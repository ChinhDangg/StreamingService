package dev.chinh.streamingservice.search.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MediaSearchRangeField {

    private String field;
    private Integer fromValue;
    private Integer toValue;
}
