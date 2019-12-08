package command

import StickerBot
import org.telegram.telegrambots.meta.api.objects.Message

class ResetCommand : TextCommand("/reset", "Rest user state") {
    override fun execute(message: Message, botAPI: StickerBot): String? {

        botAPI.setUserState(message.from!!.id, message.chatId, UserState.EMPTY)
        return null
    }
}