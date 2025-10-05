package dev.chinh.streamingservice.search.data;

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
    protected String title;
    protected List<String> tags;
    protected List<String> characters;
    protected List<String> universes;
    protected List<String> authors;
    protected Integer year;

    public void validate() {
        boolean hasAny =
                (validateSearchString(title)) ||
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
