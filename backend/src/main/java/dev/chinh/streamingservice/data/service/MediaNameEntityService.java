package dev.chinh.streamingservice.data.service;

import dev.chinh.streamingservice.data.dto.MediaNameEntry;
import dev.chinh.streamingservice.data.repository.MediaAuthorRepository;
import dev.chinh.streamingservice.data.repository.MediaCharacterRepository;
import dev.chinh.streamingservice.data.repository.MediaTagRepository;
import dev.chinh.streamingservice.data.repository.MediaUniverseRepository;
import dev.chinh.streamingservice.search.constant.SortBy;
import lombok.RequiredArgsConstructor;
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
        return new PageImpl<>(nameEntries, PageRequest.of(entry.getNumber(), entry.getSize()), entry.getTotalElements());
    }

    private Pageable getPageable(int offset, SortBy sortBy, Sort.Direction sortOrder) {
        final int pageSize = 20;
        return PageRequest.of(offset, pageSize, Sort.by(sortOrder, sortBy.getField()));
    }
}
