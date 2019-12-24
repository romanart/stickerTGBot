package command

import bot.StickerBot
import org.telegram.telegrambots.meta.api.objects.Message

class HelpCommand(command: String, hidden: Boolean, additionalMessage: String) :
    TextCommand(command, hidden, "Print this message $additionalMessage") {

    override fun execute(message: Message, botAPI: StickerBot): String? {
        return botAPI.textCommands.asSequence().filter { !it.hidden }.joinToString("\n") { it.name + " - " + it.description }
    }
}