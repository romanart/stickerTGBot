package command

import org.telegram.telegrambots.meta.api.objects.Message
import bot.StickerBot
import org.telegram.telegrambots.meta.api.methods.stickers.DeleteStickerFromSet
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker

class DeleteCommand : TextCommand("/delete", "Delete replied sticker from stickerPack") {
    override fun execute(message: Message, botAPI: StickerBot): String? {
        val replyToMessage = message.replyToMessage ?: return "Use /delete command with reply"
        val sticker = replyToMessage.sticker ?: return "Please replay to message which contains sticker"

        if (botAPI.isMyOwnSticker(sticker.setName)) {
            deleteStickerImpl(sticker, botAPI)
            return "Sticker has been deleted from set ${sticker.setName}"
        }

        return "I can only delete stickers which I have created by my own (${sticker.setName})"
    }

    private fun deleteStickerImpl(sticker: Sticker, botAPI: StickerBot) {
        try {
            botAPI.botLogger.info { "Delete sticker from set ${sticker.setName}" }
            botAPI.execute(DeleteStickerFromSet(sticker.fileId))
        } catch (e: Throwable) {
            // nothing todo
        }
    }
}
