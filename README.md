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

### 3. Настройка переменных окружения

Создайте файл `.env` или установите переменные окружения§

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

## TODO

- [ ] Добавить JWT middleware для защиты API
- [ ] Реализовать rate limiting
- [ ] Обновить фронтенд для работы с новым API
- [ ] Миграции базы данных
- [ ] Тесты
