package database

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet


class MySQLDatabaseLocalConnection(user: String, databaseName: String) : DatabaseConnection {

    private fun makeConnection(user: String, databaseName: String): Connection {
        Class.forName("com.mysql.jdbc.Driver")
        return DriverManager.getConnection(
            "jdbc:mysql://localhost/$databaseName?serverTimezone=UTC", user, ""
        )
    }

    private val connection = makeConnection(user, databaseName)

    override fun <R> executeQuery(sql: String, processor: (ResultSet) -> R): R {

        return connection.createStatement().use {
            processor(it.executeQuery(sql))
        }
    }

    override fun executeUpdate(sql: String) {
        connection.createStatement().use {
            it.executeUpdate(sql)
        }
    }

    override fun releaseConnection() {
        connection.close()
    }

}