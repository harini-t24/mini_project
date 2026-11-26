
package oracletest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection
{
    public static Connection getConnection() throws SQLException
    {
        String url = "jdbc:oracle:thin:@//HP:1539/orclpdb";
        String username = "system";
        String password = "Pass#123";

        return DriverManager.getConnection(url, username, password);
    }
}
