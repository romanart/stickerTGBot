import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File

@Serializable
class Config(val botName: String, val botToken: String, val ownerID: Long)

fun parseConfig(file: File): Config {
    val json = Json(JsonConfiguration.Stable)
    return json.parse(Config.serializer(), file.readText())
}