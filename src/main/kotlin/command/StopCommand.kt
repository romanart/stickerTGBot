package command

import bot.StickerBot
import org.telegram.telegrambots.meta.api.objects.Message
import kotlin.system.exitProcess

class StopCommand(private val ownerId: Long) : TextCommand("/stop", true, "Shutdown the bot") {
    override fun execute(message: Message, botAPI: StickerBot): String? {
        if (ownerId == message.chatId) {
            botAPI.onClosing()
            exitProcess(0)
        }
        return null
    }
}