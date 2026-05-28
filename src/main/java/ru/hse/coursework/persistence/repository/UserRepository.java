package ru.hse.coursework.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.hse.coursework.persistence.entity.UserEntity;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByChatId(Long chatId);
}
