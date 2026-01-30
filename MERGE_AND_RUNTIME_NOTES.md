# Разрешение конфликтов слияния и заметки по runtime-ошибкам

## Выполнено

### 1. Разрешены конфликты слияния в 3 файлах

- **TransferLiftableGlobal.java** — принята логика входящей ветки (глобальная зона CarrierIn с fallback на выбор пользователя).
- **FillWaterskinsGlobal.java** — объединён javadoc и логика HEAD (вода/чай, бурдюки и чайники, 4-параметровые методы).
- **NGameUI.java** — сохранены оба виджета: `simpleRoutesWidget` и `dbStatsOverlay`.

### 2. Ошибка компиляции DbStatsOverlay

- Добавлена заглушка: `src/nurgling/widgets/DbStatsOverlay.java` (extends `haven.Widget`).
- Сборка проходит успешно.

### 3. Проверка ClassNotFoundException (FlowerMenu$Petal, PosLight$1, GItem$Amount, Resource$3)

- В `build/classes/haven/` все нужные классы есть: `FlowerMenu$Petal.class`, `PosLight$1.class`, `GItem$Amount.class`, `Resource$3.class`.
- В `bin/hafen.jar` они тоже присутствуют (в т.ч. `haven/FlowerMenu$Petal.class` и др.).
- В `hafen-res.jar` и `builtin-res.jar` классов пакета `haven` не найдено — они наши классы не перекрывают.

То есть сборка и упаковка корректны; внутренние классы haven в JAR есть.

## Рекомендации при повторении ClassNotFoundException

1. **Чистая пересборка**
   ```bash
   ant clean
   ant run
   ```
   Убедитесь, что запускаете именно из корня проекта (`c:\Game\Lanfir-nurgling2`).

2. **Запуск без Ant**
   - Перейти в каталог `bin`: `cd bin`
   - Запустить: `java -jar hafen.jar`
   Так вы убедитесь, что используются JAR из `bin/` и classpath из манифеста (все JAR рядом с hafen.jar).

3. **Версии JAR ресурсов**
   - `builtin-res.jar` и `hafen-res.jar` скачиваются с `http://www.havenandhearth.com/java/`.
   - Если клиент и ресурсы разных версий, возможны несовместимости. При необходимости перекачайте эти JAR (через `ant clean` и повторную сборку с загрузкой ресурсов).

4. **JDK**
   - Проект собирается под Java 8 (`source/target="1.8"`). Проверьте, что для `ant run` и для ручного `java -jar` используется одна и та же JDK 8 (или совместимая).

Если после чистого `ant clean` и `ant run` ошибки останутся, имеет смысл сохранить полный лог запуска и стек-трейс и проверить, не загружаются ли классы из другого classpath или другого JAR.
