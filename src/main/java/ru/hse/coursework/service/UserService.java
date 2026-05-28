package ru.hse.coursework.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hse.coursework.persistence.entity.UserEntity;
import ru.hse.coursework.persistence.repository.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void rememberTelegramUser(Long chatId, String username, String firstName) {
        if (chatId == null) {
            return;
        }
        UserEntity user = userRepository.findByChatId(chatId).orElseGet(UserEntity::new);
        user.setChatId(chatId);
        user.setUsername(blankToNull(username));
        user.setFirstName(blankToNull(firstName));
        userRepository.save(user);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
