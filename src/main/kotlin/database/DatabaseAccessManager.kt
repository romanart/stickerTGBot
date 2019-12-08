package database

import DatabaseConfig

class DatabaseAccessManager(private val databaseConfig: DatabaseConfig) {
    fun createDatabaseConnection(): DatabaseConnection {
        return when (databaseConfig.type) {
            "aws" -> AWSDatabaseConnection(databaseConfig)
            "local" -> MySQLDatabaseLocalConnection(databaseConfig.user, databaseConfig.databaseName)
            else -> error("Unknown type of DB ${databaseConfig.type}")
        }
    }
}