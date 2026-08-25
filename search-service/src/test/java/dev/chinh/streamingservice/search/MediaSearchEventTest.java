package dev.chinh.streamingservice.search;

import dev.chinh.streamingservice.common.constant.MediaNameEntityConstant;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.search.data.NameEntityField;
import dev.chinh.streamingservice.search.event.MediaSearchEventService;
import dev.chinh.streamingservice.search.persistence.*;
import dev.chinh.streamingservice.search.service.OpenSearchSearchService;
import dev.chinh.streamingservice.search.service.OpenSearchService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class MediaSearchEventTest {

    @Mock
    private MediaMapper mockMediaMapper;
    private final MediaMapper mediaMapper = new MediaMapperImpl();
    @Mock
    private GrouperMetadataRepository grouperMetadataRepository;
    @Mock
    private OpenSearchService openSearchService;
    @Mock
    private OpenSearchSearchService searchService;
    @InjectMocks
    private MediaSearchEventService mediaSearchEventService;

    @Mock
    private EntityManager entityManager;

    private MediaUpdateEvent.MediaCreatedReadyForSearch createDefaultMediaCreatedReadyForSearchEvent() {
        return new MediaUpdateEvent.MediaCreatedReadyForSearch(
                1L, 1L, "Title 1", "Bucket 1", "Key 1", "Thumbnail 1",
                "Preview 1", 1, 1L, 1, 1, Instant.MIN, (short) 2026, MediaType.ALBUM,
                Map.of(1L, "tag 1"), Map.of(1L, "char 1"), Map.of(1L, "uni 1"), Map.of(1L, "author 1"),
                12L, 11L, "numInfo 1"
        );
    }

    @Test
    void shouldMapMediaCreatedEventToMediaSearchItem() {
        var event = createDefaultMediaCreatedReadyForSearchEvent();

        MediaSearchItem result = mediaMapper.map(event);
        assertThat(result.getGroupInfo().getId()).isEqualTo(event.groupInfoId());
        assertThat(result.getGroupInfo().getGrouperId()).isEqualTo(event.groupInfoGrouperId());
        assertThat(result.getGroupInfo().getNumInfo()).isEqualTo(event.groupInfoNumInfo());
    }

    @Test
    void testHandleCreateMediaIndexSearchIsGrouperChild() throws IOException {
        var event = createDefaultMediaCreatedReadyForSearchEvent();

        GrouperMediaMetadata mappedMetadata = new GrouperMediaMetadata();
        when(mockMediaMapper.mapToGrouperMediaMetadata(event)).thenReturn(mappedMetadata);

        MediaGroupInfo mockParentGrouperInfo = mock(MediaGroupInfo.class);

        when(entityManager.getReference(MediaGroupInfo.class, 11L))
                .thenReturn(mockParentGrouperInfo);

        mediaSearchEventService.handleCreateMediaIndexSearch(event);

        ArgumentCaptor<GrouperMediaMetadata> metadataCaptor = ArgumentCaptor.forClass(GrouperMediaMetadata.class);
        verify(grouperMetadataRepository).save(metadataCaptor.capture());

        GrouperMediaMetadata savedMetadata = metadataCaptor.getValue();
        assertThat(savedMetadata).isNotNull();
        assertThat(savedMetadata).isSameAs(mappedMetadata);
        assertThat(savedMetadata.isNew()).isTrue();

        MediaGroupInfo savedGroupInfo = savedMetadata.getGroupInfo();
        assertThat(savedGroupInfo).isNotNull();
        assertThat(savedGroupInfo.getId()).isEqualTo(event.groupInfoId());
        assertThat(savedGroupInfo.getNumInfo()).isEqualTo(event.groupInfoNumInfo());
        assertThat(savedGroupInfo.getGrouperInfo()).isSameAs(mockParentGrouperInfo);
        assertThat(savedGroupInfo.getGrouperMediaMetadata()).isSameAs(savedMetadata);

        // Verify OpenSearch and the first branch are NOT invoked
        verify(openSearchService, never()).indexDocument(any(), anyLong(), any());
    }

    @Test
    void testHandleCreateMediaIndexSearchIsGrouper() throws IOException {
        var event = new MediaUpdateEvent.MediaCreatedReadyForSearch(
                1L, 1L, "Title 1", "Bucket 1", "Key 1", "Thumbnail 1",
                "Preview 1", 1, 1L, 1, 1, Instant.MIN, (short) 2026, MediaType.GROUPER,
                Map.of(1L, "tag 1"), Map.of(1L, "char 1"), Map.of(1L, "uni 1"), Map.of(1L, "author 1"),
                11L, 11L, null
        );

        GrouperMediaMetadata mappedMetadata = new GrouperMediaMetadata();
        when(mockMediaMapper.mapToGrouperMediaMetadata(event)).thenReturn(mappedMetadata);

        MediaSearchItem mappedSearchItem = mock(MediaSearchItem.class);
        when(mockMediaMapper.map(event)).thenReturn(mappedSearchItem);
        when(mappedSearchItem.getId()).thenReturn(11L);

        mediaSearchEventService.handleCreateMediaIndexSearch(event);

        verify(grouperMetadataRepository).save(mappedMetadata);

        assertThat(mappedMetadata.isNew()).isTrue();

        MediaGroupInfo savedGroupInfo = mappedMetadata.getGroupInfo();
        assertThat(savedGroupInfo).isNotNull();
        assertThat(savedGroupInfo.getId()).isEqualTo(event.groupInfoId());
        assertThat(savedGroupInfo.getNumInfo()).isNull();
        assertThat(savedGroupInfo.getGrouperInfo()).isNull();

        verify(openSearchService).indexDocument(eq(OpenSearchService.MEDIA_INDEX_NAME), eq(11L), eq(mappedSearchItem));
    }

    @ParameterizedTest
    @EnumSource(value = MediaType.class, names = {"GROUPER", "ALBUM", "VIDEO"})
    void testHandleDeleteMediaIndexSearch(MediaType mediaType) throws IOException {
        long mediaId = 123L;

        mediaSearchEventService.handleDeleteMediaIndexSearch(mediaId, mediaType);

        if (mediaType == MediaType.GROUPER || mediaType == MediaType.ALBUM) {
            verify(grouperMetadataRepository).deleteById(mediaId);
            verify(openSearchService).deleteDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaId);
        } else {
            verify(openSearchService).deleteDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaId);
        }
    }

    @Test
    void testHandleUpdateMediaNameEntitySearch() throws IOException {
        long mediaId = 123L;
        MediaNameEntityConstant nameEntityConstant = MediaNameEntityConstant.AUTHORS;
        Map<Long, String> nameEntityIdsToNames = Map.of(1L, "Author 1");

        mediaSearchEventService.handleUpdateMediaNameEntitySearch(mediaId, nameEntityConstant, nameEntityIdsToNames);

        Map<String, Object> expectedDoc = Map.of(nameEntityConstant.getName(), nameEntityIdsToNames);
        verify(openSearchService).partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaId, expectedDoc);
    }

    @Test
    void testHandleUpdateMediaTitleSearch() throws IOException {
        long mediaId = 123L;
        String userId = "user123";
        String title = "New Title";

        assertThrows(NullPointerException.class, () -> mediaSearchEventService.handleUpdateMediaTitleSearch(userId, mediaId, null));

        mediaSearchEventService.handleUpdateMediaTitleSearch(userId, mediaId, title);

        verify(openSearchService).partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaId, Map.of(ContentMetaData.TITLE, title));
    }

    @Test
    void testHandleUpdateMediaThumbnailSearch() throws IOException {
        long mediaId = 123L;
        String newThumbnail = "new-thumbnail.jpg";

        mediaSearchEventService.handleUpdateMediaThumbnailSearch(mediaId, newThumbnail);

        verify(openSearchService).partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaId, Map.of(ContentMetaData.THUMBNAIL, newThumbnail));
    }

    @Test
    void testHandleUpdateMediaLengthSearch() throws IOException {
        List<Long> mediaIds = List.of(123L);
        int delta = 10;
        long version = 456L;

        mediaSearchEventService.handleUpdateMediaLengthSearch(mediaIds, delta, version);

        verify(openSearchService).updateNumericFieldWithDeltaByIds(OpenSearchService.MEDIA_INDEX_NAME, mediaIds, ContentMetaData.LENGTH, delta, String.valueOf(version));
    }

    @Test
    void testHandleUpdateMediaPreview() throws IOException {
        long mediaId = 123L;
        String previewObject = "objectkey.mp4";

        mediaSearchEventService.handleUpdateMediaPreview(mediaId, previewObject);

        verify(openSearchService).partialUpdateDocument(OpenSearchService.MEDIA_INDEX_NAME, mediaId, Map.of(ContentMetaData.PREVIEW, previewObject));
    }

    @Test
    void testHandleMoveGrouperItemNewParentIsGrouperAndOldParentIsGrouper() throws IOException {
        var event = new MediaUpdateEvent.GrouperItemMoved(
                "user123", 111L, 110L, "child-file-name", true, 11L
        );

        GrouperMediaMetadata grouperMediaMetadata = new GrouperMediaMetadata();
        when(entityManager.getReference(GrouperMediaMetadata.class, event.childMediaId()))
                .thenReturn(grouperMediaMetadata);

        MediaGroupInfo mockParentGrouperInfo = mock(MediaGroupInfo.class);
        when(entityManager.getReference(MediaGroupInfo.class, event.parentMediaId()))
                .thenReturn(mockParentGrouperInfo);

        mediaSearchEventService.handleMoveGrouperItem(event);

        MediaGroupInfo savedGroupInfo = grouperMediaMetadata.getGroupInfo();
        assertThat(savedGroupInfo).isNotNull();
        assertThat(savedGroupInfo.getId()).isEqualTo(event.newGroupInfoId());
        assertThat(savedGroupInfo.getNumInfo()).isEqualTo(event.fileName());
        assertThat(savedGroupInfo.getGrouperInfo()).isSameAs(mockParentGrouperInfo);
        assertThat(savedGroupInfo.getGrouperMediaMetadata()).isSameAs(grouperMediaMetadata);

        verify(grouperMetadataRepository).save(grouperMediaMetadata);
        verify(openSearchService).deleteDocument(OpenSearchService.MEDIA_INDEX_NAME, event.childMediaId());
    }

    @Test
    void testHandleMoveGrouperItemNewParentIsGrouperAndOldParentIsNotGrouper() throws IOException {
        var event = new MediaUpdateEvent.GrouperItemMoved(
                "123", 111L, 110L, "child-file-name", false, 11L
        );

        MediaSearchItem mockMediaSearchItem = new MediaSearchItem();

        Hit<MediaSearchItem> mockHit = mock(Hit.class);
        HitsMetadata<MediaSearchItem> mockHitsContainer = mock(HitsMetadata.class);
        SearchResponse<MediaSearchItem> mockSearchResponse = mock(SearchResponse.class);

        when(mockHit.source()).thenReturn(mockMediaSearchItem);
        when(mockHitsContainer.hits()).thenReturn(List.of(mockHit));
        when(mockSearchResponse.hits()).thenReturn(mockHitsContainer);

        when(searchService.findById(
                eq(OpenSearchService.MEDIA_INDEX_NAME),
                anyLong(),
                anyLong(),
                eq(MediaSearchItem.class)
        )).thenReturn(mockSearchResponse);

        GrouperMediaMetadata grouperMediaMetadata = new GrouperMediaMetadata();
        when(mockMediaMapper.mapToGrouperMediaMetadata(mockMediaSearchItem))
                .thenReturn(grouperMediaMetadata);

        mediaSearchEventService.handleMoveGrouperItem(event);

        assertThat(mockSearchResponse.hits().hits().getFirst().source()).isSameAs(mockMediaSearchItem);

        assertThat(grouperMediaMetadata.isNew()).isTrue();
        verify(grouperMetadataRepository).save(grouperMediaMetadata);
        verify(openSearchService).deleteDocument(OpenSearchService.MEDIA_INDEX_NAME, event.childMediaId());
    }

    @Test
    void testHandleMoveGrouperItemNewParentIsNotGrouper() throws IOException {
        var event = new MediaUpdateEvent.GrouperItemMoved(
                "123", 111L, null, "child-file-name", true, 11L
        );

        mediaSearchEventService.handleMoveGrouperItem(event);

        verify(grouperMetadataRepository, never()).save(any(GrouperMediaMetadata.class));
        verify(grouperMetadataRepository).deleteById(event.childMediaId());
        verify(openSearchService).deleteDocument(OpenSearchService.MEDIA_INDEX_NAME, event.childMediaId());
    }

    @Test
    void testHandleCreateNameEntitySearch() throws IOException {
        MediaNameEntityConstant nameEntityConstant = MediaNameEntityConstant.AUTHORS;
        long nameEntityId = 123L;
        NameEntityField nameEntityField = new NameEntityField(
                nameEntityId,
                12L,
                "Author 2",
                2,
                "thumbnail.jpg"
        );

        mediaSearchEventService.handleCreateNameEntitySearch(nameEntityConstant, nameEntityId, nameEntityField);

        verify(openSearchService).indexDocument(nameEntityConstant.getName(), nameEntityId, nameEntityField);
    }

    @Test
    void testHandleDeleteNameEntitySearch() throws IOException {
        long nameEntityId = 123L;
        MediaNameEntityConstant nameEntityConstant = MediaNameEntityConstant.AUTHORS;

        mediaSearchEventService.handleDeleteNameEntitySearch(nameEntityId, nameEntityConstant);

        verify(openSearchService).deleteDocument(nameEntityConstant.getName(), nameEntityId);
    }

    @Test
    void testHandleUpdateNameEntitySearch() throws IOException {
        MediaNameEntityConstant nameEntityConstant = MediaNameEntityConstant.AUTHORS;
        long nameEntityId = 123L;
        String newName = "New Author";
        String oldThumbnail = "old-thumbnail.jpg";
        String newThumbnail = "new-thumbnail.jpg";

        mediaSearchEventService.handleUpdateNameEntitySearch(nameEntityConstant, nameEntityId, newName, oldThumbnail, newThumbnail);

        verify(openSearchService).partialUpdateDocument(nameEntityConstant.getName(), nameEntityId, Map.of(
                ContentMetaData.NAME, newName,
                ContentMetaData.THUMBNAIL, newThumbnail
        ));
        verify(openSearchService).updateAllNestedFieldNameWithIdInIndex(
                OpenSearchService.MEDIA_INDEX_NAME,
                nameEntityConstant.getName(),
                nameEntityId,
                ContentMetaData.NAME,
                newName
        );
    }

    @Test
    void testHandleUpdateNameEntityLength() throws IOException {
        MediaNameEntityConstant nameEntityConstant = MediaNameEntityConstant.AUTHORS;
        Long[] nameEntityIds = new Long[] {1L, 2L};
        int deltaLength = -1;

        mediaSearchEventService.handleUpdateNameEntityLength(nameEntityConstant, nameEntityIds, deltaLength);

        verify(openSearchService).updateNumericFieldWithDeltaByIds(
                eq(nameEntityConstant.getName()),
                eq(List.of(nameEntityIds)),
                eq(ContentMetaData.LENGTH),
                eq(deltaLength),
                anyString()
        );
    }
}
