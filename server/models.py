import uuid
from datetime import datetime, timezone

from flask_sqlalchemy import SQLAlchemy
from sqlalchemy.dialects.postgresql import UUID, JSONB

db = SQLAlchemy()


class Zone(db.Model):
    """
    Модель зоны на сервере.

    Основные решения:
    - Первичный ключ: UUID (тип PostgreSQL UUID), генерируется на сервере, но
      клиент может прислать свой uuid (для обратной совместимости).
    - zone_sync: строковый идентификатор "мира/сервака/профиля" для
      разделения зон разных миров (как вы хотели).
    - last_updated: сервер хранит "истинное" время последнего изменения.
      Клиент присылает timestamp, мы сравниваем и принимаем только более
      новые изменения.
    - deleted: soft-delete, чтобы можно было синхронизировать удаления.
    """

    __tablename__ = "zones"

    id = db.Column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
        unique=True,
        nullable=False,
    )

    # Для синхронизации между клиентами удобнее иметь "внешний" UUID, которым
    # могут обмениваться разные клиенты. Его можно сделать равным id, но
    # оставим отдельным полем на случай миграций / смены схемы.
    uuid = db.Column(
        db.String(100),
        unique=False,
        nullable=False,
    )

    name = db.Column(db.String(100), nullable=False)
    path = db.Column(db.String(255), nullable=True)

    # Цвет и прочие сложные структуры храним в JSONB, чтобы можно было
    # удобно работать в PostgreSQL.
    color = db.Column(JSONB, nullable=False, default=dict)
    space = db.Column(JSONB, nullable=False, default=dict)

    # Специализации и списки предметов (вход/выход) тоже как JSON
    spec = db.Column(JSONB, nullable=True)
    in_zone = db.Column(JSONB, nullable=True)
    out_zone = db.Column(JSONB, nullable=True)

    last_updated = db.Column(
        db.DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(timezone.utc),
    )

    deleted = db.Column(db.Boolean, default=False, nullable=False)

    # Идентификатор "пространства синхронизации" — мир / сервер / профиль
    zone_sync = db.Column(db.String(100), nullable=False, index=True)

    __table_args__ = (
        # На уровне логики синхронизации удобно требовать уникальность
        # (zone_sync, uuid). Это значит: один и тот же uuid не может быть
        # одновременно в двух разных мирах, но внутри одного мира он уникален.
        db.UniqueConstraint("zone_sync", "uuid", name="uq_zone_sync_uuid"),
    )

    def to_dict(self):
        return {
            "uuid": self.uuid,
            "name": self.name,
            "path": self.path,
            "color": self.color,
            "space": self.space,
            "spec": self.spec,
            "in": self.in_zone,
            "out": self.out_zone,
            "last_updated": self.last_updated.isoformat(),
            "deleted": self.deleted,
            "zone_sync": self.zone_sync,
        }


def init_db(app):
    """
    Инициализация SQLAlchemy и создание таблиц.
    Вызывается из create_app.
    """
    # Проверяем, что DATABASE_URL установлен и валиден
    db_url = app.config.get("SQLALCHEMY_DATABASE_URI")
    if not db_url or db_url == "":
        error_msg = (
            "SQLALCHEMY_DATABASE_URI is not set. "
            "Please set DATABASE_URL environment variable. "
            "In Railway: Add PostgreSQL service to your project, it will automatically create DATABASE_URL."
        )
        print(f"ERROR: {error_msg}")
        raise ValueError(error_msg)
    
    if not db_url.startswith(("postgresql://", "postgres://")):
        error_msg = f"Invalid DATABASE_URL format. Expected postgresql:// or postgres://, got: {db_url[:50]}..."
        print(f"ERROR: {error_msg}")
        raise ValueError(error_msg)
    
    print(f"Initializing database connection...")
    db.init_app(app)
    
    try:
        with app.app_context():
            print("Creating database tables...")
            db.create_all()
            print("Database tables created successfully")
    except Exception as e:
        print(f"Error creating database tables: {e}")
        raise


