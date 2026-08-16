package dev.chinh.streamingservice.search.data;

import lombok.*;
import java.util.Set;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MediaSearchField {

    private String field;
    private Set<Object> values;
    private boolean matchAll;

    public static boolean validateSearchString(String string) {
        return string != null && !string.isBlank() && string.length() >= 2 && string.length() <= 100;
    }

    public boolean hasAny() {
        return field != null && !field.isBlank() && values != null && !values.isEmpty();
    }
}
