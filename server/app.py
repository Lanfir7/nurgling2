from datetime import datetime, timezone
import os
import uuid

from flask import Flask, request, jsonify
from flask_cors import CORS

from config import get_config
from models import db, init_db, Zone


def create_app():
    app = Flask(__name__)
    cfg = get_config()
    app.config.from_object(cfg)

    # CORS, чтобы игра (клиент) могла ходить на этот API
    # Если в CORS_ORIGINS есть "*", разрешаем все origin'ы
    if "*" in cfg.CORS_ORIGINS:
        CORS(app, resources={r"/*": {"origins": "*"}})
    else:
        CORS(app, resources={r"/*": {"origins": cfg.CORS_ORIGINS}})

    # Инициализация БД
    try:
        init_db(app)
    except Exception as e:
        print(f"Failed to initialize database: {e}")
        print("Please check DATABASE_URL environment variable")
        raise

    @app.route("/health", methods=["GET"])
    def health():
        return jsonify({"status": "ok"}), 200

    @app.route("/time", methods=["GET"])
    def get_server_time():
        """
        Возвращает текущее серверное время в UTC в формате ISO 8601.
        Используется для синхронизации времени между клиентом и сервером.
        """
        server_time = datetime.now(timezone.utc)
        return jsonify({
            "server_time": server_time.isoformat(),
            "timestamp": server_time.timestamp()
        }), 200

    # === API синхронизации зон ===

    @app.route("/zones", methods=["GET"])
    def get_zones():
        """
        Получение зон.
        Параметры:
        - zone_sync: обязательный идентификатор мира/сервера/профиля
        - updated_after: ISO8601 timestamp; если указан, отдаём только зоны,
          изменённые позже этой даты.
        """
        zone_sync = request.args.get("zone_sync")
        if not zone_sync:
            return jsonify({"error": "zone_sync is required"}), 400

        updated_after = request.args.get("updated_after")
        # ВАЖНО: По умолчанию не возвращаем удаленные зоны
        # Клиент должен явно запросить их, если нужно
        q = Zone.query.filter_by(zone_sync=zone_sync, deleted=False)

        if updated_after:
            try:
                dt = datetime.fromisoformat(updated_after)
                if dt.tzinfo is None:
                    dt = dt.replace(tzinfo=timezone.utc)
            except ValueError:
                return jsonify({"error": "Invalid updated_after format (use ISO8601)"}), 400

            q = q.filter(Zone.last_updated > dt)

        zones = q.all()
        return jsonify([z.to_dict() for z in zones]), 200

    @app.route("/zones", methods=["POST"])
    def upsert_zone():
        """
        Добавление или обновление зоны.
        Тело запроса (JSON):
        {
          "uuid": "client-uuid-... (строка)",
          "name": "...",
          "path": "...",
          "color": {...},
          "space": {...},
          "spec": [...],
          "in": [...],
          "out": [...],
          "last_updated": "2025-01-01T12:00:00Z",
          "deleted": false,
          "zone_sync": "world-1"
        }

        Правило синхронизации:
        - по (zone_sync, uuid) ищем запись;
        - сравниваем client_last_updated с server_last_updated;
        - если клиент новее — принимаем изменения;
        - если сервер новее — игнорируем и возвращаем сообщение.
        """
        if not request.is_json:
            return jsonify({"error": "Request body must be JSON"}), 400

        data = request.get_json()

        zone_sync = data.get("zone_sync")
        if not zone_sync:
            return jsonify({"error": "zone_sync is required"}), 400

        # Внешний UUID зоны. Если клиент не прислал — генерируем.
        ext_uuid = data.get("uuid")
        if not ext_uuid:
            ext_uuid = str(uuid.uuid4())

        name = data.get("name")
        if not name:
            return jsonify({"error": "name is required"}), 400

        client_ts_str = data.get("last_updated")
        if not client_ts_str:
            return jsonify({"error": "last_updated is required"}), 400

        try:
            client_ts = datetime.fromisoformat(client_ts_str)
            if client_ts.tzinfo is None:
                client_ts = client_ts.replace(tzinfo=timezone.utc)
        except ValueError:
            return jsonify({"error": "Invalid last_updated format"}), 400

        zone = Zone.query.filter_by(zone_sync=zone_sync, uuid=ext_uuid).first()

        if zone:
            server_ts = zone.last_updated
            if server_ts.tzinfo is None:
                server_ts = server_ts.replace(tzinfo=timezone.utc)

            if client_ts <= server_ts:
                # Серверные данные новее или равны — ничего не меняем
                # Но все равно возвращаем серверное время для синхронизации
                return jsonify(
                    {
                        "message": "Server data is up-to-date",
                        "server_last_updated": server_ts.isoformat(),
                        "last_updated": server_ts.isoformat()  # Для синхронизации времени
                    }
                ), 200

            # Принимаем изменение от клиента
            # ВАЖНО: Используем время клиента, если оно новее серверного
            # Это сохраняет оригинальную дату создания зоны
            # Но не позволяем времени быть в будущем (защита от проблем с синхронизацией времени)
            current_server_time = datetime.now(timezone.utc)
            if client_ts > server_ts:
                # Клиент новее - используем время клиента, но не в будущем
                # Используем минимум из времени клиента и текущего времени сервера
                zone_time = min(client_ts, current_server_time)
            else:
                # Это не должно произойти из-за проверки выше, но на всякий случай
                zone_time = current_server_time
            
            zone.name = name
            zone.path = data.get("path")
            zone.color = data.get("color") or {}
            zone.space = data.get("space") or {}
            zone.spec = data.get("spec") or []
            zone.in_zone = data.get("in") or []
            zone.out_zone = data.get("out") or []
            zone.deleted = bool(data.get("deleted", False))
            # ВАЖНО: Сохраняем время клиента, а не текущее время сервера
            zone.last_updated = zone_time
        else:
            # Создаём новую зону
            # ВАЖНО: Для новых зон используем время клиента, если оно предоставлено
            # Это сохраняет оригинальную дату создания зоны
            if client_ts_str:
                try:
                    # Используем время клиента, но не позволяем ему быть в будущем
                    server_time = min(client_ts, datetime.now(timezone.utc))
                except:
                    server_time = datetime.now(timezone.utc)
            else:
                server_time = datetime.now(timezone.utc)
            
            zone = Zone(
                uuid=ext_uuid,
                name=name,
                path=data.get("path"),
                color=data.get("color") or {},
                space=data.get("space") or {},
                spec=data.get("spec") or [],
                in_zone=data.get("in") or [],
                out_zone=data.get("out") or [],
                deleted=bool(data.get("deleted", False)),
                last_updated=server_time,  # Используем время клиента для новых зон
                zone_sync=zone_sync,
            )
            db.session.add(zone)

        db.session.commit()
        # Возвращаем серверное время в ответе, чтобы клиент мог синхронизировать
        return jsonify({
            "message": "Zone upserted",
            "uuid": ext_uuid,
            "last_updated": zone.last_updated.isoformat()
        }), 200

    @app.route("/zones/<string:ext_uuid>", methods=["GET"])
    def get_zone(ext_uuid: str):
        """
        Получить конкретную зону по её внешнему UUID.
        Параметр:
        - ?zone_sync=... обязателен, чтобы различать миры.
        """
        zone_sync = request.args.get("zone_sync")
        if not zone_sync:
            return jsonify({"error": "zone_sync is required"}), 400

        zone = Zone.query.filter_by(zone_sync=zone_sync, uuid=ext_uuid).first()
        if not zone:
            return jsonify({"error": "Zone not found"}), 404

        return jsonify(zone.to_dict()), 200

    @app.route("/zones/<string:ext_uuid>", methods=["DELETE"])
    def delete_zone(ext_uuid: str):
        """
        Soft-delete зоны: выставляем deleted = true и обновляем last_updated.
        Параметр:
        - ?zone_sync=...
        """
        zone_sync = request.args.get("zone_sync")
        if not zone_sync:
            return jsonify({"error": "zone_sync is required"}), 400

        zone = Zone.query.filter_by(zone_sync=zone_sync, uuid=ext_uuid).first()
        if not zone:
            return jsonify({"error": "Zone not found"}), 404

        zone.deleted = True
        zone.last_updated = datetime.now(timezone.utc)
        db.session.commit()

        return jsonify({"message": "Zone deleted"}), 200

    # === Простой веб‑интерфейс для просмотра зон ===

    @app.route("/manager")
    def manager():
        """
        Очень простой HTML интерфейс:
        - выбор zone_sync
        - таблица зон
        - кнопка обновления
        """
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Zone Manager</title>
    <style>
        body { font-family: Arial, sans-serif; padding: 20px; }
        table { border-collapse: collapse; width: 100%; margin-top: 10px; }
        th, td { border: 1px solid #ccc; padding: 6px 8px; text-align: left; }
        th { background: #f0f0f0; }
        .controls { margin-bottom: 10px; }
        input, button { padding: 4px 6px; }
    </style>
</head>
<body>
    <h1>Zone Manager</h1>
    <div class="controls">
        <label>zone_sync:
            <input id="zoneSync" type="text" value="world-1"/>
        </label>
        <button onclick="loadZones()">Load zones</button>
    </div>
    <table id="zonesTable">
        <thead>
            <tr>
                <th>UUID</th>
                <th>Name</th>
                <th>Path</th>
                <th>Last updated</th>
                <th>Deleted</th>
            </tr>
        </thead>
        <tbody></tbody>
    </table>
    <script>
        async function loadZones() {
            const zoneSync = document.getElementById('zoneSync').value;
            if (!zoneSync) {
                alert('zone_sync is required');
                return;
            }
            const res = await fetch('/zones?zone_sync=' + encodeURIComponent(zoneSync));
            const data = await res.json();
            const tbody = document.querySelector('#zonesTable tbody');
            tbody.innerHTML = '';
            data.forEach(z => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td>${z.uuid}</td>
                    <td>${z.name}</td>
                    <td>${z.path || ''}</td>
                    <td>${z.last_updated}</td>
                    <td>${z.deleted ? 'yes' : 'no'}</td>
                `;
                tbody.appendChild(tr);
            });
        }
    </script>
</body>
</html>
        """

    return app


# Создаём приложение для Railway (WSGI сервер будет импортировать app)
app = create_app()

if __name__ == "__main__":
    # Для локального запуска
    port = int(os.environ.get("PORT", "8080"))
    print(f"Starting server on port {port}...")
    db_url = app.config.get("SQLALCHEMY_DATABASE_URI", "")
    if db_url:
        print(f"Database URL: {db_url[:50]}...")
    else:
        print("WARNING: DATABASE_URL is not set!")
    app.run(host="0.0.0.0", port=port, debug=False)


