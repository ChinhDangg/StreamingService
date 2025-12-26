package dev.chinh.streamingservice.searchclient;

import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.searchclient.constant.SortBy;
import dev.chinh.streamingservice.searchclient.data.MediaSearchRangeField;
import dev.chinh.streamingservice.searchclient.data.SearchFieldGroup;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.*;
import org.opensearch.client.opensearch._types.analysis.*;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.TextQueryType;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.indices.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OpenSearchService {

    private final OpenSearchClient client;

    public <T> SearchResponse<T> searchContaining(String index, String field, String text, Class<T> clazz) throws IOException {
        return client.search(s -> s
                        .index(index)
                        .query(q -> q
                                .match(m -> m
                                        .field(field)
                                        .query(FieldValue.of(text))
                                )
                        ),
                clazz
        );
    }

    public SearchResponse<Object> advanceSearch(String index,
                                                List<SearchFieldGroup> includeGroups,
                                                List<SearchFieldGroup> excludeGroups,
                                                List<MediaSearchRangeField> mediaSearchRanges,
                                                int page, int size, SortBy sortBy, SortOrder sortOrder) throws IOException {
        BoolQuery.Builder rootBool = new BoolQuery.Builder();

        if (mediaSearchRanges != null) {
            for (MediaSearchRangeField mediaSearchRange : mediaSearchRanges) {
                RangeQuery.Builder rangeBuilder = new RangeQuery.Builder();
                rangeBuilder.field(mediaSearchRange.getField());
                if (mediaSearchRange.getFrom() != null)
                    rangeBuilder.gte(JsonData.of(mediaSearchRange.getFrom()));
                if (mediaSearchRange.getTo() != null)
                    rangeBuilder.lte(JsonData.of(mediaSearchRange.getTo()));
                rootBool.filter(Query.of(q -> q.range(rangeBuilder.build())));
            }
        }

        if (includeGroups != null) {
            for (SearchFieldGroup group : includeGroups) {
                String field = group.getField();
                Set<Object> values = group.getValues();
                if (values == null || values.isEmpty()) continue;

                BoolQuery.Builder groupBool = new BoolQuery.Builder();

                if (group.isMatchAll()) {
                    for (Object value : values) {
                        groupBool.must(buildTermOrMatch(field, value, group.isSearchTerm()));
                    }
                } else {
                    for (Object value : values) {
                        groupBool.should(buildTermOrMatch(field, value, group.isSearchTerm()));
                    }
                    groupBool.minimumShouldMatch("1");
                }
                rootBool.must(Query.of(q -> q.bool(groupBool.build())));
            }
        }

        if (excludeGroups != null) {
            for (SearchFieldGroup group : excludeGroups) {
                String field = group.getField();
                Set<Object> values = group.getValues();
                if (values == null || values.isEmpty()) continue;

                for (Object value : values) {
                    rootBool.mustNot(buildTermOrMatch(field, value, group.isSearchTerm()));
                }
            }
        }

        Query rootQuery = Query.of(q -> q.bool(rootBool.build()));

        return searchWithQuery(index, rootQuery, page, size, sortBy, sortOrder, true);
    }

    private Query buildTermOrMatch(String field, Object value, boolean searchTerm) {
        if (searchTerm) {
            // Term Query
            return Query.of(q -> q
                    .term(t -> t
                            .field(field)
                            .value(FieldValue.of(value.toString()))
                    )
            );
        } else {
            // Match Query
            return Query.of(q -> q
                    .match(m -> m
                            .field(field)
                            .query(FieldValue.of(value.toString()))
                    )
            );
        }
    }

    public SearchResponse<Object> search(String index, Object text, int page, int size, SortBy sortBy,
                                    SortOrder sortOrder) throws IOException {
        Query multiMatch = Query.of(q -> q
                .multiMatch(m -> m
                        .query(text.toString())
                        .fields(
                                ContentMetaData.TITLE + "^3.0",
                                ContentMetaData.UNIVERSES + ".search^2.0",
                                ContentMetaData.CHARACTERS + ".search^2.0",
                                ContentMetaData.TAGS + ".search^1.5",
                                ContentMetaData.AUTHORS + ".search^1.0"
                        )
                        .prefixLength(1)
                        .fuzziness("AUTO")
                        .type(TextQueryType.BestFields)
                )
        );
        return searchWithQuery(index, multiMatch, page, size, sortBy, sortOrder, true);
    }

    /**
     * Search exactly with given search strings by field.
     */
    public SearchResponse<Object> searchTermByOneField(String index, String field, List<Object> text, boolean matchAll, int page, int size,
                                               SortBy sortBy, SortOrder sortOrder) throws IOException {
        BoolQuery.Builder termBoolBuilder = new BoolQuery.Builder();
        if (matchAll) {
            for (Object term : text) {
                termBoolBuilder.must(buildTermOrMatch(field, term, true));
            }
        } else {
            for (Object term : text) {
                termBoolBuilder.should(buildTermOrMatch(field, term, true));
            }
            termBoolBuilder.minimumShouldMatch("1");
        }
        Query termBoolQuery = Query.of(q -> q.bool(termBoolBuilder.build()));
        return searchWithQuery(index, termBoolQuery, page, size, sortBy, sortOrder, true);
    }

    public SearchResponse<Object> searchMatchAll(String index, int page, int size, SortBy sortBy, SortOrder sortOrder) throws IOException {
        Query matchAll = Query.of(q -> q
                .matchAll(m -> m)
        );
        return searchWithQuery(index, matchAll, page, size, sortBy, sortOrder, false);
    }

    private SearchResponse<Object> searchWithQuery(String index, Query query, int page, int size, SortBy sortBy,
                                                   SortOrder sortOrder, boolean useScoreTieBreaker) throws IOException {

        String sortByField = switch (sortBy) {
            case SortBy.UPLOAD_DATE -> ContentMetaData.UPLOAD_DATE;
            case SortBy.LENGTH -> ContentMetaData.LENGTH;
            case SortBy.SIZE -> ContentMetaData.SIZE;
            case SortBy.YEAR -> ContentMetaData.YEAR;
            default -> null;
        };

        SortOptions primarySort = SortOptions.of(o -> o.field(f -> f
                .field(sortByField)
                .order(sortOrder)
        ));

        SortOptions standardTieBreaker = SortOptions.of(o -> o.field(f -> f
                .field("_id")
                .order(SortOrder.Asc)
        ));

        SortOptions scoreTieBreaker = SortOptions.of(o -> o.score(s -> s.order(sortOrder)));

        SortOptions sortTieBreaker = useScoreTieBreaker ? scoreTieBreaker : standardTieBreaker;

        SearchResponse<Object> response = client.search(s -> s
                .index(index)
                .from(page * size)
                .size(size)
                .query(query)
                .sort(primarySort, sortTieBreaker)
                .trackTotalHits(t -> t.count(1000)), Object.class
        );

//        response.hits().hits().forEach(h -> {
//            String source = h.source().toString();
//
//            int uploadDateIndex = source.indexOf(ContentMetaData.UPLOAD_DATE);
//            String uploadDateString = source.substring(uploadDateIndex + ContentMetaData.UPLOAD_DATE.length());
//            int commaIndex = uploadDateString.indexOf("],");
//            String uploadDate = uploadDateString.substring(0, commaIndex + 1);
//            System.out.println(uploadDate);
//        });

        return response;
    }
}