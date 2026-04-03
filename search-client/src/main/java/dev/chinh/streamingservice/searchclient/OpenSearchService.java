package dev.chinh.streamingservice.searchclient;

import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.searchclient.constant.SortBy;
import dev.chinh.streamingservice.searchclient.data.MediaSearchRangeField;
import dev.chinh.streamingservice.searchclient.data.SearchFieldGroup;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.*;
import org.opensearch.client.opensearch._types.query_dsl.*;
import org.opensearch.client.opensearch.core.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OpenSearchService {

    private final OpenSearchClient client;

    public <T> SearchResponse<T> searchContaining(long userId, String index, String field, String text, Class<T> clazz) throws IOException {
        BoolQuery.Builder rootBool = new BoolQuery.Builder();
        rootBool.filter(buildTermOrMatch(ContentMetaData.USER_ID, FieldValue.of(userId), true));
        rootBool.must(buildTermOrMatch(field, FieldValue.of(text), false));
        return client.search(s -> s
                        .index(index)
                        .query(Query.of(q -> q
                                .bool(rootBool.build()))
                        ),
                clazz
        );
    }

    public SearchResponse<Object> advanceSearch(long userId,
                                                String index,
                                                List<SearchFieldGroup> includeGroups,
                                                List<SearchFieldGroup> excludeGroups,
                                                List<MediaSearchRangeField> mediaSearchRanges,
                                                int page, int size, SortBy sortBy, SortOrder sortOrder) throws IOException {
        BoolQuery.Builder rootBool = new BoolQuery.Builder();

        rootBool.filter(buildTermOrMatch(ContentMetaData.USER_ID, FieldValue.of(userId), true));

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
                        groupBool.must(buildTermOrMatch(field, FieldValue.of(value.toString()), group.isSearchTerm()));
                    }
                } else {
                    for (Object value : values) {
                        groupBool.should(buildTermOrMatch(field, FieldValue.of(value.toString()), group.isSearchTerm()));
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
                    rootBool.mustNot(buildTermOrMatch(field, FieldValue.of(value.toString()), group.isSearchTerm()));
                }
            }
        }

        Query rootQuery = Query.of(q -> q.bool(rootBool.build()));

        return searchWithQuery(index, rootQuery, page, size, sortBy, sortOrder, true);
    }

    private Query buildTermOrMatch(String field, FieldValue value, boolean isTermQuery) {
        Query leafQuery = Query.of(q -> {
            if (isTermQuery) {
                return q.term(t -> t.field(field).value(value));
            } else {
                return q.match(m -> m.field(field).query(value));
            }
        });
        // if field contains a dot (nameEntity.name), wrap it in a nested query
        if (field.contains(".")) {
            String path = field.split("\\.")[0];
            return Query.of(q -> q
                    .nested(n -> n
                            .path(path)
                            .query(leafQuery)
                    )
            );
        }
        return leafQuery;
    }

    public SearchResponse<Object> search(long userId, String index, Object text, int page, int size, SortBy sortBy,
                                    SortOrder sortOrder) throws IOException {
        Query multiMatchNested = Query.of(q -> q
                .bool(b -> b
                        .filter(buildTermOrMatch(ContentMetaData.USER_ID, FieldValue.of(userId), true))
                        // Root level field (Title)
                        .should(s -> s
                                .match(m -> m
                                        .field(ContentMetaData.TITLE)
                                        .query(FieldValue.of(text.toString()))
                                        .boost(3.0f)
                                        .fuzziness("AUTO")
                                        .prefixLength(1)
                                )
                        )
                        // Nested Field: Universes
                        .should(s -> s
                                .nested(n -> n
                                        .path(ContentMetaData.UNIVERSES)
                                        .query(nq -> nq
                                                .match(m -> m
                                                        .field(ContentMetaData.UNIVERSES + ".name")
                                                        .query(FieldValue.of(text.toString()))
                                                        .fuzziness("AUTO")
                                                        .prefixLength(1)
                                                )
                                        )
                                        .scoreMode(ChildScoreMode.Max)
                                        .boost(2.0f)
                                )
                        )
                        // Nested Field: Characters
                        .should(s -> s
                                .nested(n -> n
                                        .path(ContentMetaData.CHARACTERS)
                                        .query(nq -> nq
                                                .match(m -> m
                                                        .field(ContentMetaData.CHARACTERS + ".name")
                                                        .query(FieldValue.of(text.toString()))
                                                        .fuzziness("AUTO")
                                                        .prefixLength(1)
                                                )
                                        )
                                        .scoreMode(ChildScoreMode.Max)
                                        .boost(2.0f)
                                )
                        )
                        // Nested Field: Tags
                        .should(s -> s
                                .nested(n -> n
                                        .path(ContentMetaData.TAGS)
                                        .query(nq -> nq
                                                .match(m -> m
                                                        .field(ContentMetaData.TAGS + ".name")
                                                        .query(FieldValue.of(text.toString()))
                                                        .fuzziness("AUTO")
                                                        .prefixLength(1)
                                                )
                                        )
                                        .scoreMode(ChildScoreMode.Max)
                                        .boost(2.0f)
                                )
                        )
                        // Nested Field: Authors
                        .should(s -> s
                                .nested(n -> n
                                        .path(ContentMetaData.AUTHORS)
                                        .query(nq -> nq
                                                .match(m -> m
                                                        .field(ContentMetaData.AUTHORS + ".name")
                                                        .query(FieldValue.of(text.toString()))
                                                        .fuzziness("AUTO")
                                                        .prefixLength(1)
                                                )
                                        )
                                        .scoreMode(ChildScoreMode.Max)
                                        .boost(2.0f)
                                )
                        )
                )
        );
        return searchWithQuery(index, multiMatchNested, page, size, sortBy, sortOrder, true);
    }

    /**
     * Search exactly with given search strings by field.
     */
    public SearchResponse<Object> searchTermByOneField(long userId, String index, String field, List<Object> text, boolean matchAll, int page, int size,
                                               SortBy sortBy, SortOrder sortOrder) throws IOException {
        BoolQuery.Builder termBoolBuilder = new BoolQuery.Builder();
        termBoolBuilder.filter(buildTermOrMatch(ContentMetaData.USER_ID, FieldValue.of(userId), true));
        if (matchAll) {
            for (Object term : text) {
                termBoolBuilder.must(buildTermOrMatch(field, FieldValue.of(term.toString()), true));
            }
        } else {
            for (Object term : text) {
                termBoolBuilder.should(buildTermOrMatch(field, FieldValue.of(term.toString()), true));
            }
            termBoolBuilder.minimumShouldMatch("1");
        }
        Query termBoolQuery = Query.of(q -> q.bool(termBoolBuilder.build()));
        return searchWithQuery(index, termBoolQuery, page, size, sortBy, sortOrder, true);
    }

    public SearchResponse<Object> searchMatchAll(String index, long userId, int page, int size, SortBy sortBy, SortOrder sortOrder) throws IOException {
        Query matchAll = Query.of(q -> q
                .bool(b -> b
                        .filter(buildTermOrMatch(ContentMetaData.USER_ID, FieldValue.of(userId), true))
                        .must(Query.of(mq -> mq
                                .matchAll(m -> m)
                        ))
                )
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