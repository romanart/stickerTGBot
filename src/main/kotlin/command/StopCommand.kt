package command

import StickerBot
import org.telegram.telegrambots.meta.api.objects.Message

class StopCommand(private val ownerId: Long) : TextCommand("/stop", true, "Stop the bot") {
    override fun execute(message: Message, botAPI: StickerBot): String? {
        if (ownerId == message.chatId) botAPI.onClosing()
        return null
    }
}