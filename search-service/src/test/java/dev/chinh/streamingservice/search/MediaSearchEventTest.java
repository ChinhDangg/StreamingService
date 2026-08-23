package dev.chinh.streamingservice.search;

import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.event.MediaUpdateEvent;
import dev.chinh.streamingservice.search.event.MediaSearchEventService;
import dev.chinh.streamingservice.search.persistence.*;
import dev.chinh.streamingservice.search.service.OpenSearchService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class MediaSearchEventTest {

    @Mock
    private MediaMapper mockMediaMapper;
    private MediaMapper mediaMapper = new MediaMapperImpl();
    @Mock
    private GrouperMetadataRepository grouperMetadataRepository;
    @Mock
    private OpenSearchService openSearchService;
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

}
