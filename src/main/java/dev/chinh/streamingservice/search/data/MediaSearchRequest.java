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
}
