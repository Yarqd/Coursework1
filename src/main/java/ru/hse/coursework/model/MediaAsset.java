package ru.hse.coursework.model;

public record MediaAsset(
        Long id,
        String storageKey,
        String telegramFileId,
        String telegramFileUniqueId,
        String mimeType,
        long fileSize,
        Integer width,
        Integer height
) {
}
