# Coursework1

MVP Telegram-бота для курсовой работы по приложению интервального повторения.

## Стек

- Java 21
- Spring Boot 3.5
- TelegramBots Java Library
- Gradle

## Запуск без токена

Приложение стартует и пишет предупреждение в лог:

```bash
./gradlew bootRun
```

## Запуск Telegram-бота

Перед запуском нужно указать токен, полученный через BotFather:

```bash
export TELEGRAM_BOT_TOKEN="..."
export TELEGRAM_BOT_USERNAME="..."
./gradlew bootRun
```
