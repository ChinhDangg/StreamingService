package dev.chinh.streamingservice.search.data;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class SearchFieldGroup {

    private String field;
    private Set<Object> values;
    private boolean searchTerm;
    private boolean matchAll; // true = AND, false = OR

    public SearchFieldGroup(String field, Set<Object> values, boolean matchAll, boolean searchTerm) {
        this.field = field;
        this.values = values;
        this.matchAll = matchAll;
        this.searchTerm = searchTerm;
    }
}
