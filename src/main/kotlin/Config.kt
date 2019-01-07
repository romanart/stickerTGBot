import kotlinx.serialization.Serializable

@Serializable
class Config(val botName: String, val botToken: String, val ownerID: Long)