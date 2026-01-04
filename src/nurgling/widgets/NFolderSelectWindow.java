package nurgling.widgets;

import haven.*;
import nurgling.*;
import nurgling.areas.*;

import javax.swing.*;
import java.util.*;

/**
 * Окно выбора папки для перемещения зоны
 */
public class NFolderSelectWindow extends Window {
    private final NArea area;
    private final NAreasWidget areasWidget;
    private Dropbox<String> folderDropbox;
    private TextEntry newFolderEntry;
    private final List<String> folders = new ArrayList<>();
    
    public NFolderSelectWindow(NArea area, NAreasWidget areasWidget) {
        super(UI.scale(new Coord(300, 150)), "Move to folder");
        this.area = area;
        this.areasWidget = areasWidget;
        
        buildFolderList();
        buildUI();
    }
    
    private void buildFolderList() {
        Set<String> folderSet = new TreeSet<>();
        
        // Собираем все папки
        for (NArea a : ((NMapView) NUtils.getGameUI().map).glob.map.areas.values()) {
            if (a.path != null && !a.path.isEmpty()) {
                folderSet.add(a.path);
                // Добавляем родительские папки
                String[] parts = a.path.split("/");
                StringBuilder sb = new StringBuilder();
                for (String part : parts) {
                    if (!part.isEmpty()) {
                        if (sb.length() > 0) sb.append("/");
                        sb.append(part);
                        folderSet.add(sb.toString());
                    }
                }
            }
        }
        
        // Добавляем корневую папку первой
        folders.add("[Root]");
        
        // Добавляем остальные папки (уже отсортированы TreeSet)
        folders.addAll(folderSet);
    }
    
    private void buildUI() {
        int y = 0;
        
        // Лейбл текущей папки
        String currentFolder = (area.path == null || area.path.isEmpty()) ? "[Root]" : area.path;
        add(new Label("Current: " + currentFolder), new Coord(0, y));
        y += UI.scale(20);
        
        // Лейбл для выпадающего списка
        add(new Label("Select folder:"), new Coord(0, y));
        y += UI.scale(18);
        
        // Выпадающий список папок
        folderDropbox = add(new Dropbox<String>(UI.scale(280), folders.size() > 10 ? 10 : folders.size(), UI.scale(16)) {
            @Override
            protected String listitem(int i) {
                return folders.get(i);
            }
            
            @Override
            protected int listitems() {
                return folders.size();
            }
            
            @Override
            protected void drawitem(GOut g, String item, int i) {
                // Показываем дружественное имя без начального /
                String displayName = item;
                if (item.startsWith("/")) {
                    displayName = item.substring(1);
                }
                g.text(displayName, new Coord(UI.scale(3), 0));
            }
        }, new Coord(0, y));
        
        // Устанавливаем текущую папку как выбранную
        int currentIdx = 0;
        for (int i = 0; i < folders.size(); i++) {
            String folder = folders.get(i);
            if (folder.equals(area.path) || 
                (folder.equals("[Root]") && (area.path == null || area.path.isEmpty()))) {
                currentIdx = i;
                break;
            }
        }
        folderDropbox.sel = folders.get(currentIdx);
        y += UI.scale(25);
        
        // Разделитель
        y += UI.scale(10);
        add(new Label("Or create new:"), new Coord(0, y));
        y += UI.scale(18);
        
        // Поле для новой папки
        newFolderEntry = add(new TextEntry(UI.scale(280), ""), new Coord(0, y));
        newFolderEntry.settip("Enter new folder name (use / for subfolders)");
        y += UI.scale(25);
        
        // Кнопки
        y += UI.scale(10);
        
        Button moveBtn = add(new Button(UI.scale(80), "Move") {
            @Override
            public void click() {
                doMove();
            }
        }, new Coord(0, y));
        
        add(new Button(UI.scale(80), "Cancel") {
            @Override
            public void click() {
                close();
            }
        }, new Coord(UI.scale(90), y));
        
        pack();
    }
    
    private void doMove() {
        String newPath;
        
        // Проверяем, введена ли новая папка
        String newFolderText = newFolderEntry.text().trim();
        if (!newFolderText.isEmpty()) {
            // Используем введённое имя новой папки
            newPath = newFolderText;
            // Убираем начальный и конечный слеш
            if (newPath.startsWith("/")) newPath = newPath.substring(1);
            if (newPath.endsWith("/")) newPath = newPath.substring(0, newPath.length() - 1);
        } else {
            // Используем выбранную папку из списка
            String selected = folderDropbox.sel;
            if (selected == null || selected.equals("[Root]")) {
                newPath = "";
            } else {
                newPath = selected;
            }
        }
        
        // Перемещаем зону
        String oldPath = area.path;
        area.path = newPath;
        area.lastLocalChange = System.currentTimeMillis();
        NConfig.needAreasUpdate();
        
        NUtils.getGameUI().msg("Moved '" + area.name + "' from '" + 
            (oldPath == null || oldPath.isEmpty() ? "[Root]" : oldPath) + "' to '" + 
            (newPath.isEmpty() ? "[Root]" : newPath) + "'");
        
        // Обновляем список в виджете
        if (areasWidget != null) {
            areasWidget.showPath(areasWidget.currentPath);
        }
        
        close();
    }
    
    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (msg.equals("close")) {
            close();
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }
    
    public void close() {
        reqdestroy();
    }
}

