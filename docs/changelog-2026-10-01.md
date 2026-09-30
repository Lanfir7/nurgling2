# Обновление графики и загрузки мира / Graphics and world loading update

## Русский

- В «Настройки Nurgling → Общие → Графика» доступны SMAA 1x, адаптивная резкость CAS и контактное затенение HBAO. Старые методы сохранены; пресет «Ультра» выбирает новые, но существующие настройки автоматически не меняются. Эффекты требуют активного Vulkan.
- Все графические настройки доступны на одной прокручиваемой странице; поиск приводит к нужному пункту.
- Исправлено падение Vulkan при подготовке рельефа с включёнными тенями. Рельеф новых текстур появляется автоматически по готовности.
- Первая подготовка графики новых объектов и их теней в Vulkan выполняется в фоне. Модели могут появляться позже, не задерживая уже готовый мир и доступные зоны опасности животных.
- Сохранение исследованной карты больше не блокирует игровой поток на время обработки всех тайлов; новые участки сохраняются и при одновременном исследовании.

Эти изменения не устраняют ожидание карты и ресурсов с сервера. Снижение рывков на новом участке леса ещё требует игрового сравнения.

## English

- Nurgling settings → General → Graphics offers SMAA 1x, adaptive CAS sharpening and HBAO contact shading. Legacy methods remain available; Ultra selects the new ones, while existing settings are not switched automatically. The effects require active Vulkan.
- All graphics options are reachable on one scrollable page, with search bringing the selected option into view.
- Fixed a Vulkan crash during relief preparation with shadows enabled. Relief on new textures appears automatically when ready.
- First-use preparation of new object graphics and their shadows runs in the background in Vulkan. Models may appear later without holding up the ready world or available animal danger zones.
- Explored-map saving no longer blocks the game thread while processing every tile; newly discovered areas are preserved during concurrent saving.

These changes do not eliminate waits for map data and server resources. Stutter reduction in a previously unseen forest still needs an in-game comparison.
