package bot

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