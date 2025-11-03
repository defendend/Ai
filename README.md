# AI Chat - Kotlin Multiplatform Web App

Веб-приложение для общения с Claude AI, созданное на Kotlin Multiplatform с поддержкой JS.

## Возможности

- Интерфейс чата для общения с Claude AI
- Сохранение API ключа в localStorage
- Современный и отзывчивый UI
- Поддержка истории сообщений
- Работа с Anthropic API

## Требования

- JDK 11 или выше
- API ключ Anthropic (получить на https://console.anthropic.com/)

## Запуск проекта

1. Склонируйте репозиторий
2. Запустите development сервер:
   ```bash
   ./gradlew jsBrowserDevelopmentRun --continuous
   ```

3. Откройте браузер на `http://localhost:8080`
4. Введите ваш API ключ Anthropic в поле в верхней части страницы
5. Начните общение с Claude!

## Сборка для продакшена

```bash
./gradlew jsBrowserProductionWebpack
```

Собранные файлы будут в `build/distributions/`

## Структура проекта

```
src/jsMain/
├── kotlin/app/
│   ├── Main.kt              # Точка входа
│   ├── ChatUI.kt            # UI логика
│   ├── AnthropicClient.kt   # API клиент
│   └── Models.kt            # Модели данных
└── resources/
    ├── index.html           # HTML страница
    └── styles.css           # Стили
```

## Деплой на defendend.dev

**ВАЖНО:** Для статического сайта НЕ нужен платный хостинг!

Подробные инструкции по деплою (включая настройку DNS для Рег.ру): [DEPLOYMENT.md](DEPLOYMENT.md)

Рекомендуемые бесплатные платформы:
- **Vercel** - проще всего, автоматический SSL
- **Cloudflare Pages** - самый быстрый CDN
- **GitHub Pages** - простой и надежный
- **Netlify** - популярный вариант

## Технологии

- Kotlin/JS
- Kotlinx Coroutines
- Kotlinx Serialization
- Anthropic Claude API
