package command

import StickerBot
import org.telegram.telegrambots.meta.api.objects.Message

class DefaultEmojy : TextCommand("/emoji", "Set default emoji for chat") {
    override fun execute(message: Message, botAPI: StickerBot): String? {
        val newEmoji = message.tokenizeCommand().drop(1).firstOrNull { checkEmodji(it) } ?:
                return "Please provide emoji"

        botAPI.setDefaultEmoji(message.chatId, newEmoji)

        return if (message.chat.isUserChat) {
            "Now default emoji is $newEmoji"
        } else "Default emoji for ${message.chat.userName} is $newEmoji"
    }
}