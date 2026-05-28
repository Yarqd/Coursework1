package ru.hse.coursework;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = "telegrambots.enabled=false")
class CourseworkBotApplicationTests {

    @Test
    void contextLoads() {
    }
}
