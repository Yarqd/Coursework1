package ru.hse.coursework.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.hse.coursework.persistence.entity.MediaAssetEntity;

public interface MediaAssetRepository extends JpaRepository<MediaAssetEntity, Long> {
}
