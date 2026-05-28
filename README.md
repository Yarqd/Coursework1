# Coursework1

Telegram-бот для интервального повторения карточек. Проект сделан на Java 21 и Spring Boot; прогресс повторений считается через FSRS, данные хранятся в PostgreSQL, картинки карточек сохраняются в MinIO.

## Стек

- Java 21
- Spring Boot 3.5
- TelegramBots Java Library
- PostgreSQL
- MinIO
- Flyway
- Gradle

## Локальная инфраструктура

Нужно установить Docker Desktop. После установки запусти Docker и подними PostgreSQL + MinIO:

```bash
cp .env.example .env
docker compose up -d
```

PostgreSQL будет доступен на `localhost:5432`, MinIO API на `localhost:9000`, консоль MinIO на `http://localhost:9001`.

Логин и пароль MinIO по умолчанию: `coursework` / `coursework-secret`.

Бакет для картинок создается приложением автоматически при первой загрузке фото.

## Запуск тестов

```bash
./gradlew test
```

Тесты используют in-memory H2 в PostgreSQL-режиме, поэтому Docker для тестов не нужен.

## Запуск Telegram-бота

Перед запуском укажи токен, полученный через BotFather:

```bash
set -a
source .env
set +a

export TELEGRAM_BOT_TOKEN="..."
export TELEGRAM_BOT_USERNAME="..."

./gradlew bootRun
```

Если токен не задан, приложение стартует без long polling и напишет предупреждение в лог.

## Графические карточки

Сценарий загрузки графической колоды:

1. Нажать «Загрузить свою колоду».
2. Выбрать «Графическая колода».
3. Отправить название колоды.
4. Для каждой карточки отправить вопрос, затем ответ. Обе стороны могут быть текстом или фото.
5. Нажать «Добавить еще карточку» или «Завершить колоду».

Метаданные карточек и прогресс лежат в PostgreSQL. Изображения лежат в MinIO, а Telegram `file_id` хранится дополнительно как быстрый способ отправлять уже загруженные картинки обратно пользователю.

## Изучение и напоминания

Кнопка «Изучать» в главном меню запускает карточки, которые актуальны сейчас:

- новые карточки, у которых еще нет FSRS-состояния;
- карточки, у которых наступил срок `due_at`.

Если в базе есть хотя бы одна карточка с наступившим сроком повторения, бот отправляет одно напоминание: «Привет, имя, пора повторить карточки, время пришло!». Повторное напоминание по тому же набору карточек не отправляется. Когда пользователь нажимает «Изучать», текущее напоминание считается обработанным.

Во время активной учебной сессии напоминания не отправляются. Сессия считается активной 30 минут после последнего действия с карточкой.

## Деплой на сервер

Рекомендуемая схема для VPS:

- код лежит в `/opt/coursework-bot/app`;
- секреты и переменные окружения лежат в `/etc/coursework-bot/coursework-bot.env`;
- PostgreSQL и MinIO запускаются через Docker Compose;
- бот запускается как `systemd`-сервис `coursework-bot`;
- сервис автоматически поднимается после перезагрузки и перезапускается при падении.

Первичная настройка сервера:

```bash
sudo REPO_URL=https://github.com/your-user/your-repo.git bash deploy/setup-server.sh
```

После этого нужно заполнить секреты:

```bash
sudo nano /etc/coursework-bot/coursework-bot.env
```

Минимально обязательно указать:

```env
TELEGRAM_BOT_TOKEN=...
TELEGRAM_BOT_USERNAME=...
DB_PASSWORD=...
POSTGRES_PASSWORD=...
MINIO_SECRET_KEY=...
MINIO_ROOT_PASSWORD=...
```

Запуск или обновление приложения:

```bash
sudo bash /opt/coursework-bot/app/deploy/deploy.sh
```

Проверка статуса:

```bash
systemctl status coursework-bot
journalctl -u coursework-bot -f
```

Остановка:

```bash
sudo systemctl stop coursework-bot
```

PostgreSQL и MinIO на сервере привязаны к `127.0.0.1`, чтобы база и объектное хранилище не были доступны из интернета напрямую.
