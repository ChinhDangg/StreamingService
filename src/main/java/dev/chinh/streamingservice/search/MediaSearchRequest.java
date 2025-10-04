package dev.chinh.streamingservice.search;

import lombok.*;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MediaSearchRequest {

    // Search fields
    protected String title;
    protected List<String> tags;
    protected List<String> characters;
    protected List<String> universes;
    protected List<String> authors;
    protected LocalDate uploadDate;
    protected Integer year;

    public void validate() {
        boolean hasAny =
                (title != null && !title.isBlank()) ||
                (tags != null && !tags.isEmpty()) ||
                (characters != null && !characters.isEmpty()) ||
                (universes != null && !universes.isEmpty()) ||
                (authors != null && !authors.isEmpty()) ||
                (uploadDate != null) ||
                (year != null);

        if (!hasAny) {
            throw new IllegalArgumentException("At least one search field must be provided");
        }
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
