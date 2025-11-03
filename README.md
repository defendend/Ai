# AI Chat - Kotlin Multiplatform

Чат-приложение с поддержкой Claude AI и DeepSeek, построенное на Kotlin Multiplatform.

## Архитектура

- **Frontend**: Kotlin/JS с DOM API
- **Backend**: Ktor (Kotlin/JVM)
- **Database**: PostgreSQL с Exposed ORM
- **Общий код**: Модели данных в `commonMain`

## Структура проекта

```
├── src/
│   ├── commonMain/        # Общий код для фронтенда и бэкенда
│   │   └── kotlin/app/
│   │       └── Models.kt  # Модели данных
│   │
│   ├── jsMain/           # Frontend (Kotlin/JS)
│   │   ├── kotlin/app/
│   │   └── resources/
│   │       ├── index.html
│   │       └── styles.css
│   │
│   └── jvmMain/          # Backend (Ktor)
│       ├── kotlin/app/
│       │   ├── Application.kt
│       │   ├── routes/
│       │   ├── services/
│       │   ├── database/
│       │   └── models/
│       └── resources/
│           └── logback.xml
```

## Требования

- JDK 17+
- PostgreSQL 14+
- Gradle 8.5+

## Настройка бэкенда

### 1. Установка PostgreSQL

```bash
# Ubuntu/Debian
sudo apt-get install postgresql postgresql-contrib

# macOS
brew install postgresql
brew services start postgresql
```

### 2. Создание базы данных

```bash
# Войти в PostgreSQL
sudo -u postgres psql

# Создать базу данных и пользователя
CREATE DATABASE aichat;
CREATE USER aichat WITH ENCRYPTED PASSWORD 'aichat';
GRANT ALL PRIVILEGES ON DATABASE aichat TO aichat;
\q
```

### 3. Настройка переменных окружения

Создайте файл `.env` или установите переменные окружения:

```bash
# Database
export DATABASE_URL="jdbc:postgresql://localhost:5432/aichat"
export DATABASE_USER="aichat"
export DATABASE_PASSWORD="aichat"

# API Keys
export CLAUDE_API_KEY="your-claude-api-key"
export DEEPSEEK_API_KEY="your-deepseek-api-key"

# JWT Secret
export JWT_SECRET="your-secure-jwt-secret-change-in-production"
```

### 4. Запуск бэкенда локально

```bash
# Собрать и запустить
./gradlew run

# Или через отдельные команды
./gradlew jvmJar
java -jar build/libs/ai-chat-jvm-1.0.0.jar
```

Backend будет доступен на `http://localhost:8080`

## API Endpoints

### Authentication

**POST** `/api/auth/register`
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**POST** `/api/auth/login`
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

## Gradle Tasks

```bash
# Frontend
./gradlew jsBrowserProductionWebpack # Production build

# Backend
./gradlew jvmJar                     # Собрать JAR
./gradlew run                        # Запустить локально

# All
./gradlew build                      # Собрать всё
```

## Автоматический деплой

Проект настроен для автоматического деплоя на Yandex Cloud VPS через GitHub Actions.

### Настройка сервера (один раз)

1. Скопируйте скрипт настройки на сервер:
```bash
scp server-setup.sh user@your-server:~
```

2. Запустите скрипт:
```bash
ssh user@your-server
sudo bash server-setup.sh
```

3. Отредактируйте файл с переменными окружения:
```bash
sudo nano /opt/ai-chat/.env
# Добавьте ваши API ключи для Claude и DeepSeek
```

4. После первого деплоя через GitHub Actions, запустите сервис:
```bash
sudo systemctl start ai-chat
sudo systemctl enable ai-chat
sudo systemctl status ai-chat
```

### Автодеплой

После настройки, при каждом пуше в main:
- **Frontend** автоматически собирается и деплоится в `/var/www/defendend.dev/`
- **Backend** автоматически собирается, деплоится в `/opt/ai-chat/` и перезапускается

Проверить статус деплоя: https://github.com/defendend/Ai/actions

Backend API доступен по адресу: `https://defendend.dev/api/`

### Логи backend

```bash
# Просмотр логов в реальном времени
sudo journalctl -u ai-chat -f

# Последние 100 строк логов
sudo journalctl -u ai-chat -n 100

# Статус сервиса
sudo systemctl status ai-chat
```

## TODO

- [ ] Добавить JWT middleware для защиты API
- [ ] Реализовать rate limiting
- [ ] Обновить фронтенд для работы с новым API
- [ ] Миграции базы данных
- [ ] Тесты
