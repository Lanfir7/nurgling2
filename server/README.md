# Zone Sync Server

Сервер для синхронизации зон между клиентами игры. Использует Flask + SQLAlchemy + PostgreSQL.

## Быстрый старт на Railway

1. **Создайте проект на Railway:**
   - Зайдите на https://railway.com/
   - Создайте новый проект
   - Выберите "Deploy from GitHub repo" и укажите этот репозиторий
   - В настройках проекта укажите:
     - **Root Directory**: `server`
     - **Start Command**: `python app.py`

2. **Добавьте PostgreSQL:**
   - В проекте Railway нажмите "+ New" → "Database" → "PostgreSQL"
   - Railway автоматически создаст переменную `DATABASE_URL`

3. **Проверьте переменные окружения:**
   - В настройках проекта → Variables должны быть:
     - `DATABASE_URL` (создаётся автоматически при добавлении PostgreSQL)
     - `PORT` (устанавливается Railway автоматически)
     - `CORS_ORIGINS` (опционально, по умолчанию разрешены все origin'ы)

4. **Деплой:**
   - Railway автоматически задеплоит приложение при пуше в репозиторий
   - Или нажмите "Deploy" вручную

## Локальная разработка

1. **Установите зависимости:**
   ```bash
   cd server
   pip install -r requirements.txt
   ```

2. **Настройте базу данных:**
   - Установите PostgreSQL локально
   - Создайте базу данных: `createdb zones_db`
   - Установите переменную окружения:
     ```bash
     export DATABASE_URL="postgresql://postgres:postgres@localhost:5432/zones_db"
     ```

3. **Запустите сервер:**
   ```bash
   python app.py
   ```

   Сервер запустится на `http://localhost:8080`

## API Endpoints

### Health Check
- `GET /health` - Проверка работоспособности сервера

### Зоны

- `GET /zones?zone_sync=<world_id>&updated_after=<ISO8601>` - Получить зоны
  - `zone_sync` (обязательно) - идентификатор мира/сервера
  - `updated_after` (опционально) - получить только зоны, обновлённые после этой даты

- `POST /zones` - Добавить/обновить зону
  ```json
  {
    "uuid": "client-uuid",
    "name": "Название зоны",
    "path": "папка/подпапка",
    "color": {...},
    "space": {...},
    "spec": [...],
    "in": [...],
    "out": [...],
    "last_updated": "2025-01-01T12:00:00Z",
    "deleted": false,
    "zone_sync": "world-1"
  }
  ```

- `GET /zones/<uuid>?zone_sync=<world_id>` - Получить конкретную зону

- `DELETE /zones/<uuid>?zone_sync=<world_id>` - Удалить зону (soft delete)

### Веб-интерфейс

- `GET /manager` - Минимальный веб-интерфейс для просмотра зон в базе данных

## Структура проекта

```
server/
├── app.py           # Основное Flask приложение
├── config.py        # Конфигурация (DATABASE_URL, CORS)
├── models.py        # SQLAlchemy модели (Zone)
├── requirements.txt # Python зависимости
├── Procfile         # Для Railway/Heroku
├── railway.toml     # Конфигурация Railway
└── README.md        # Эта документация
```

## Решение проблем

### Ошибка "Could not parse SQLAlchemy URL"

**Причина:** `DATABASE_URL` не установлен или имеет неправильный формат.

**Решение:**
1. Убедитесь, что PostgreSQL добавлен в Railway проект
2. Проверьте, что переменная `DATABASE_URL` существует в настройках проекта
3. Railway автоматически создаёт `DATABASE_URL` при добавлении PostgreSQL

### Ошибка подключения к базе данных

**Причина:** Неправильные credentials или база данных не создана.

**Решение:**
1. Проверьте `DATABASE_URL` в Railway → Variables
2. Убедитесь, что PostgreSQL сервис запущен
3. Для локальной разработки проверьте, что PostgreSQL запущен и база данных создана

## Примечания

- Сервер использует **soft delete** для зон (поле `deleted`)
- Синхронизация основана на сравнении `last_updated` - принимаются только более новые изменения
- `zone_sync` позволяет разделять зоны разных миров/серверов/профилей

