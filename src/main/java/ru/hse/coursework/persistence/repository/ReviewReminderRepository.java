package ru.hse.coursework.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.hse.coursework.persistence.entity.ReviewReminderEntity;

public interface ReviewReminderRepository extends JpaRepository<ReviewReminderEntity, Long> {
}
