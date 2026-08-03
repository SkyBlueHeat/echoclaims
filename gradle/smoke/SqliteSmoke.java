import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Run against the shaded JAR only. Unit tests use the un-shadowed classpath, so they cannot
 * catch packaging faults such as relocating sqlite-jdbc away from the JNI symbols exported
 * by its bundled native library.
 */
public final class SqliteSmoke {

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        Path database = directory.resolve("smoke.db");
        Files.deleteIfExists(database);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE smoke (id INTEGER PRIMARY KEY)");
            statement.execute("INSERT INTO smoke (id) VALUES (1)");

            try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM smoke")) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new IllegalStateException("SQLite round-trip failed");
                }
            }
        }

        System.out.println("shadowJar SQLite smoke test passed: " + database);
    }
}
