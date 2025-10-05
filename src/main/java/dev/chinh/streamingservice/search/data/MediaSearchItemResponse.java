package dev.chinh.streamingservice.search.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaSearchItemResponse extends MediaSearchRequest {

    protected String id;
    protected String thumbnail;
    protected int length;
    protected LocalDate uploadDate;
}
