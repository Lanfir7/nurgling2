package monitoring;

import haven.Gob;
import nurgling.NInventory;
import nurgling.NUtils;
import nurgling.tasks.NTask;


import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ContainerWatcher  implements Runnable {
    Gob parentGob;
    public java.sql.Connection connection;
    final String sql = "INSERT INTO containers (hash, grid_id, coord) VALUES (?, ?, ?)";
    public ContainerWatcher(Gob parentGob) {
        this.parentGob = parentGob;
    }

    @Override
    public void run() {
        try {
            // Раньше здесь использовался блокирующий NTask, который ждал,
            // пока заполнится hash и gcoord. Это блокировало поток пула БД
            // и мешало выполнению других задач (в том числе RecipeHashFetcher).
            // Сейчас просто проверяем значения и, если данных нет, пропускаем запись.
            if (parentGob == null || parentGob.ngob == null ||
                    parentGob.ngob.hash == null || parentGob.ngob.gcoord == null) {
                // Недостаточно данных для записи контейнера – просто выходим
                return;
            }

            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, parentGob.ngob.hash);
            preparedStatement.setLong(2, parentGob.ngob.grid_id);
            preparedStatement.setString(3, parentGob.ngob.gcoord.toString());

            preparedStatement.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            if (e.getSQLState() != null && !e.getSQLState().equals("23505")) {  // Код ошибки для нарушения уникальности
                e.printStackTrace();
            }
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                rollbackException.printStackTrace();
            }
        }
    }
}