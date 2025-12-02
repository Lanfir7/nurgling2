import os


class Config:
    # URL подключения к PostgreSQL. Railway прокидывает DATABASE_URL в переменные окружения.
    # Railway может предоставлять URL в формате postgres://, но SQLAlchemy требует postgresql://
    database_url = os.environ.get("DATABASE_URL", "")
    
    # Если DATABASE_URL начинается с postgres://, заменяем на postgresql://
    if database_url.startswith("postgres://"):
        database_url = database_url.replace("postgres://", "postgresql://", 1)
    
    # Если DATABASE_URL не установлен, используем fallback для локальной разработки
    if not database_url:
        # Для локальной разработки можно использовать SQLite или локальный PostgreSQL
        database_url = os.environ.get(
            "LOCAL_DB_URL",
            "postgresql://postgres:postgres@localhost:5432/zones_db"
        )
        print(f"WARNING: DATABASE_URL not set, using fallback: {database_url[:30]}...")
        print("This is OK for local development, but Railway requires DATABASE_URL to be set!")
    else:
        # Показываем только начало URL (без пароля)
        safe_url = database_url.split("@")[-1] if "@" in database_url else database_url[:30]
        print(f"Using DATABASE_URL from environment: ...@{safe_url}")
    
    SQLALCHEMY_DATABASE_URI = database_url
    SQLALCHEMY_TRACK_MODIFICATIONS = False

    # Разрешённые origin'ы для CORS (клиенты игры + локальная отладка)
    CORS_ORIGINS = os.environ.get(
        "CORS_ORIGINS",
        "http://localhost:8000,http://127.0.0.1:8000,http://localhost:8080,http://127.0.0.1:8080,*",
    ).split(",")


def get_config():
    return Config()


