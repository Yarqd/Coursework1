package ru.hse.coursework.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hse.coursework.model.MediaAsset;
import ru.hse.coursework.persistence.entity.MediaAssetEntity;
import ru.hse.coursework.persistence.repository.MediaAssetRepository;
import ru.hse.coursework.storage.ObjectStorageService;
import ru.hse.coursework.storage.StoredObject;

@Service
public class MediaAssetService {
    private final MediaAssetRepository mediaAssetRepository;
    private final ObjectStorageService objectStorageService;

    public MediaAssetService(MediaAssetRepository mediaAssetRepository, ObjectStorageService objectStorageService) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.objectStorageService = objectStorageService;
    }

    @Transactional
    public MediaAsset storeTelegramPhoto(
            String telegramFileId,
            String telegramFileUniqueId,
            byte[] bytes,
            Integer width,
            Integer height,
            Long fileSize
    ) {
        StoredObject storedObject = objectStorageService.putImage(bytes, "jpg", "image/jpeg");
        MediaAssetEntity entity = new MediaAssetEntity();
        entity.setStorageKey(storedObject.storageKey());
        entity.setTelegramFileId(telegramFileId);
        entity.setTelegramFileUniqueId(telegramFileUniqueId);
        entity.setMimeType(storedObject.mimeType());
        entity.setFileSize(fileSize == null ? storedObject.size() : fileSize);
        entity.setWidth(width);
        entity.setHeight(height);
        return toModel(mediaAssetRepository.save(entity));
    }

    private MediaAsset toModel(MediaAssetEntity entity) {
        return new MediaAsset(
                entity.getId(),
                entity.getStorageKey(),
                entity.getTelegramFileId(),
                entity.getTelegramFileUniqueId(),
                entity.getMimeType(),
                entity.getFileSize(),
                entity.getWidth(),
                entity.getHeight()
        );
    }
}
