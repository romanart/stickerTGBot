package database

import java.sql.ResultSet

interface DatabaseConnection {


    fun <R> executeQuery(sql: String, processor: (ResultSet) -> R): R

    fun executeUpdate(sql: String)

    fun releaseConnection()
}