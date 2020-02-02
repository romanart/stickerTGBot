package bot

import database.DatabaseConnection
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage

abstract class BotTask(val time: Long) : Comparable<BotTask> {

    abstract fun execute(botApi: StickerBot)

    override fun compareTo(other: BotTask): Int = (time - other.time).toInt()
}

class MessageCleanUpTask(private val chatId: Long, private val messageId: Int, time: Long) : BotTask(time) {
    override fun execute(botApi: StickerBot) {
        botApi.execute(DeleteMessage(chatId, messageId))
    }
}

abstract class DropTableTask(private val tableName: String, private val dbConnection: DatabaseConnection, time: Long) :
    BotTask(time) {
    override fun execute(botApi: StickerBot) {
        dbConnection.executeUpdate("TRUNCATE TABLE $tableName;")
        postProcess()
    }

    protected abstract fun postProcess()
}