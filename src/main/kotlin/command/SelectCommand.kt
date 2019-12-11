package command

import StickerBot
import org.telegram.telegrambots.meta.api.objects.Message

class SelectCommand : TextCommand("/select", "Select sticker pack to work with") {

    override fun execute(message: Message, botAPI: StickerBot): String? {

        if (message.chat.isUserChat) {
            if (!message.hasSticker() && message.replyToMessage == null) {
                botAPI.setUserState(message.from!!.id, message.chatId, UserState.SELECT)
                return null
            }
        }

        val replyMessage = message.replyToMessage ?: return "Please replay to sticker which you like to select"
        val stickerPack = replyMessage.sticker ?: return "Replied message has to contain sticker"

        if (botAPI.isMyOwnSticker(stickerPack.setName)) {
            botAPI.setCurrentStickerPack(message.from!!.id, message.chatId, stickerPack.setName)
            return "${stickerPack.setName} is selected ${if (message.chat.isGroupChat) "for " + message.from.userName else ""}"
        }

        return "Its in not my sticker pack ${stickerPack.setName}"
    }
}

class SelectSpecialCommand : SpecialCommand(UserState.SELECT, "Send me a sticker work with") {
    override fun execute(message: Message, botAPI: StickerBot): String? {
        val stickerPack = message.sticker ?: return "Send me a sticker"

        if (botAPI.isMyOwnSticker(stickerPack.setName)) {
            botAPI.setCurrentStickerPack(message.from.id, message.chatId, stickerPack.setName)
            botAPI.setUserState(message.from.id, message.chatId, UserState.ADD)
            return "${stickerPack.setName} is selected"
        }

        return "Its in not my sticker pack ${stickerPack.setName}"
    }
}

class SelectGroupCommand : TextCommand("/gselect", "Select group sticker pack") {
    override fun execute(message: Message, botAPI: StickerBot): String? {
        val sticker = message.replyToMessage?.sticker ?: return "Reply with sticker"
        val stickerSetName = sticker.setName.also {
            if (!botAPI.isMyOwnSticker(it)) return "Sorry, it's now my sticker pack"
        }

        val stickerOwner = botAPI.getStickerPackOwner(stickerSetName)
        if (stickerOwner < 0) return "Sorry, I don't know who is owner of sticker pack"

        botAPI.setGroupStickerPack(message.chatId, stickerSetName)
        return null
    }
}