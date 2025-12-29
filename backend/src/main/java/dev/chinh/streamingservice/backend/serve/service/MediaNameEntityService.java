package dev.chinh.streamingservice.backend.serve.service;

import dev.chinh.streamingservice.backend.content.service.MinIOService;
import dev.chinh.streamingservice.backend.content.service.ThumbnailService;
import dev.chinh.streamingservice.common.constant.MediaType;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.persistence.projection.MediaNameEntry;
import dev.chinh.streamingservice.persistence.repository.MediaAuthorRepository;
import dev.chinh.streamingservice.persistence.repository.MediaCharacterRepository;
import dev.chinh.streamingservice.persistence.repository.MediaTagRepository;
import dev.chinh.streamingservice.persistence.repository.MediaUniverseRepository;
import dev.chinh.streamingservice.searchclient.constant.SortBy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaNameEntityService {

    private final MediaAuthorRepository mediaAuthorRepository;
    private final MediaCharacterRepository mediaCharacterRepository;
    private final MediaUniverseRepository mediaUniverseRepository;
    private final MediaTagRepository mediaTagRepository;
    private final ThumbnailService thumbnailService;
    private final MinIOService minIOService;

    @Value("${always-show-original-resolution}")
    private String alwaysShowOriginalResolution;

    public Page<MediaNameEntry> findAllAuthors(int offset, SortBy sortBy, Sort.Direction sortOrder) {
        return mapInfo(mediaAuthorRepository.findAllNames(getPageable(offset, sortBy, sortOrder)), false);
    }

    public Page<MediaNameEntry> findAllCharacters(int offset, SortBy sortBy, Sort.Direction sortOrder) {
        return mapInfo(mediaCharacterRepository.findAllNames(getPageable(offset, sortBy, sortOrder)), true);
    }

    public Page<MediaNameEntry> findAllUniverses(int offset, SortBy sortBy, Sort.Direction sortOrder) {
        return mapInfo(mediaUniverseRepository.findAllNames(getPageable(offset, sortBy, sortOrder)), true);
    }

    public Page<MediaNameEntry> findAllTags(int offset, SortBy sortBy, Sort.Direction sortOrder) {
        return mapInfo(mediaTagRepository.findAllNames(getPageable(offset, sortBy, sortOrder)), false);
    }

    private Page<MediaNameEntry> mapInfo(Page<MediaNameEntry> entry, boolean hasThumbnail) {
        List<MediaNameEntry> nameEntries = entry.getContent();
        if (hasThumbnail) {
            if (Boolean.parseBoolean(alwaysShowOriginalResolution)) {
                nameEntries.forEach(nameEntry -> {
                    try {
                        nameEntry.setThumbnail(minIOService.getSignedUrlForHostNginx(ContentMetaData.THUMBNAIL_BUCKET, nameEntry.getThumbnail(), 60 * 60));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } else {
                thumbnailService.processThumbnails(nameEntries); // process thumbnails first with the original thumbnail path
                // set thumbnail path to the directory of the thumbnail location
                nameEntries.forEach(nameEntry -> {
                    try {
                        nameEntry.setThumbnail(ThumbnailService.getThumbnailPath(nameEntry.getName(), nameEntry.getThumbnail()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
        return new PageImpl<>(nameEntries, PageRequest.of(entry.getNumber(), entry.getSize()), entry.getTotalElements());
    }

    private Pageable getPageable(int offset, SortBy sortBy, Sort.Direction sortOrder) {
        final int pageSize = 20;
        return PageRequest.of(offset, pageSize, Sort.by(sortOrder, sortBy.getField()));
    }
}
