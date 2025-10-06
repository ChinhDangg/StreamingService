package dev.chinh.streamingservice.search.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.chinh.streamingservice.data.ContentMetaData;
import lombok.*;

import java.lang.reflect.Field;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MediaSearchRequest {

    // Search fields
    @JsonProperty(ContentMetaData.TITLE)
    protected String title;
    @JsonProperty(ContentMetaData.TAGS)
    protected List<String> tags;
    @JsonProperty(ContentMetaData.CHARACTERS)
    protected List<String> characters;
    @JsonProperty(ContentMetaData.UNIVERSES)
    protected List<String> universes;
    @JsonProperty(ContentMetaData.AUTHORS)
    protected List<String> authors;
    @JsonProperty(ContentMetaData.YEAR)
    protected Integer year;
    @JsonProperty(ContentMetaData.LENGTH)
    protected Integer length;

    public void validate() {
        boolean hasAny =
                (validateSearchString(title)) ||
                (length != null) ||
                (tags != null && !tags.isEmpty()) ||
                (characters != null && !characters.isEmpty()) ||
                (universes != null && !universes.isEmpty()) ||
                (authors != null && !authors.isEmpty()) ||
                (year != null);

        if (!hasAny) {
            throw new IllegalArgumentException("At least one search field must be provided");
        }
    }

    public static boolean validateSearchString(String string) {
        return string != null && !string.isBlank() && string.length() >= 2 && string.length() <= 100;
    }

    public static void validateFieldName(String fieldName) {
        boolean found = false;
        Field[] fields = MediaSearchRequest.class.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            if (field.getName().equals(fieldName)) {
                found = true;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Unknown field name: " + fieldName);
        }
    }
}
