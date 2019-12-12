package database

import java.util.*

class DatabasePingTask(private val databaseConnection: DatabaseConnection) : TimerTask() {
    private fun ping() {
        databaseConnection.executeQuery("SELECT 1") { /* nothing to do */ }
    }

    override fun run() {
        ping()
    }
}