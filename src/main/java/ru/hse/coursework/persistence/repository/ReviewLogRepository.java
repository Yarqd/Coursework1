package ru.hse.coursework.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.hse.coursework.persistence.entity.ReviewLogEntity;

public interface ReviewLogRepository extends JpaRepository<ReviewLogEntity, Long> {
}
