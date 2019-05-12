import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON
import java.io.File

@Serializable
class Config(val botName: String, val botToken: String, val ownerID: Long)

fun parseConfig(file: File) = JSON.parse(Config.serializer(), file.readText())