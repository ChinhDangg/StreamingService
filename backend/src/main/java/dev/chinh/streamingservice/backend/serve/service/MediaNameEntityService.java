package dev.chinh.streamingservice.backend.serve.service;

import dev.chinh.streamingservice.backend.content.service.MinIOService;
import dev.chinh.streamingservice.backend.content.service.ThumbnailService;
import dev.chinh.streamingservice.common.data.ContentMetaData;
import dev.chinh.streamingservice.mediapersistence.projection.NameEntityDTO;
import dev.chinh.streamingservice.mediapersistence.repository.MediaAuthorRepository;
import dev.chinh.streamingservice.mediapersistence.repository.MediaCharacterRepository;
import dev.chinh.streamingservice.mediapersistence.repository.MediaTagRepository;
import dev.chinh.streamingservice.mediapersistence.repository.MediaUniverseRepository;
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

    public Page<NameEntityDTO> findAllAuthors(String userId, int offset, SortBy sortBy, Sort.Direction sortOrder) {
        return mapInfo(userId, mediaAuthorRepository.findAllNames(Long.parseLong(userId), getPageable(offset, sortBy, sortOrder)), false);
    }

    public Page<NameEntityDTO> findAllCharacters(String userId, int offset, SortBy sortBy, Sort.Direction sortOrder) {
        return mapInfo(userId, mediaCharacterRepository.findAllNames(Long.parseLong(userId), getPageable(offset, sortBy, sortOrder)), true);
    }

    public Page<NameEntityDTO> findAllUniverses(String userId, int offset, SortBy sortBy, Sort.Direction sortOrder) {
        return mapInfo(userId, mediaUniverseRepository.findAllNames(Long.parseLong(userId), getPageable(offset, sortBy, sortOrder)), true);
    }

    public Page<NameEntityDTO> findAllTags(String userId, int offset, SortBy sortBy, Sort.Direction sortOrder) {
        return mapInfo(userId, mediaTagRepository.findAllNames(Long.parseLong(userId), getPageable(offset, sortBy, sortOrder)), false);
    }

    private Page<NameEntityDTO> mapInfo(String userId, Page<NameEntityDTO> entry, boolean hasThumbnail) {
        List<NameEntityDTO> nameEntries = entry.getContent();
        if (hasThumbnail) {
            if (Boolean.parseBoolean(alwaysShowOriginalResolution)) {
                nameEntries.forEach(nameEntry -> {
                    try {
                        nameEntry.setThumbnail(minIOService.getObjectUrl(ContentMetaData.THUMBNAIL_BUCKET, nameEntry.getThumbnail()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } else {
                thumbnailService.processThumbnails(userId, nameEntries); // process thumbnails first with the original thumbnail path
                // set thumbnail path to the directory of the thumbnail location
                nameEntries.forEach(nameEntry -> {
                    try {
                        nameEntry.setThumbnail(ThumbnailService.getThumbnailPath(ThumbnailService.getThumbnailUrlParentPath(), nameEntry.getName(), nameEntry.getThumbnail()));
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
