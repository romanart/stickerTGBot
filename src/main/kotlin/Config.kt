import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File

@Serializable
class DatabaseConfig(val host: String, val port: Int, val region: String, val databaseName: String, val type: String, val user: String, val password: String?, val certificate: String)

@Serializable
class Config(val botName: String, val botToken: String, val ownerID: Long, val databaseConfig: DatabaseConfig)

fun parseConfig(file: File): Config {
    val json = Json(JsonConfiguration.Stable)
    return json.parse(Config.serializer(), file.readText())
}