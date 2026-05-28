package ru.hse.coursework.storage;

public record StoredObject(String storageKey, String mimeType, long size) {
}
