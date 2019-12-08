package command

import ImageProvider
import StickerBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.stickers.AddStickerToSet
import org.telegram.telegrambots.meta.api.objects.File
import org.telegram.telegrambots.meta.api.objects.Message

private class AddExecutor(private val imageProvider: ImageProvider) {


    private fun addStickerToStickerSet(message: Message, setName: String, botAPI: StickerBot): String {
        val sticker = message.sticker!!
        val fileID = sticker.fileId
        val fileResponse = botAPI.execute(GetFile().setFileId(fileID)) as File
        val file = botAPI.downloadFile(fileResponse)
        val userId = message.from!!.id
        botAPI.execute(AddStickerToSet(userId, setName, sticker.emoji).setPngSticker(file))
        return "Added new sticker to ${setName.toStickerURL}"
    }

    private fun addPhotoToStickerSet(
        message: Message,
        setName: String,
        emoji: String,
        botAPI: StickerBot
    ): String {
        val fileID = message.extractPhoto()
        val filePhoto = botAPI.execute(GetFile().setFileId(fileID)) as File

        val file = botAPI.downloadFile(filePhoto)

        val convertedImage = imageProvider.getImageFile(file)
        val userId = message.from!!.id

        botAPI.execute(AddStickerToSet(userId, setName, emoji).setPngSticker(convertedImage))
        return "Add new sticker to ${setName.toStickerURL}"
    }

    private fun extractEmoji(message: Message) = message.tokenizeCommand().firstOrNull { checkEmodji(it) }

    fun execute(message: Message, botAPI: StickerBot, isSpecial: Boolean): String? {
        try {
            val currentStickerSet =
                botAPI.getCurrentStickerSet(message.from.id, message.chatId) ?: return "Please chose sticker pack first"
            val emoji = extractEmoji(message) ?: botAPI.getDefaultEmojy(message.chatId) ?: "☺️"
            if (message.hasPhoto()) return addPhotoToStickerSet(message, currentStickerSet, emoji, botAPI)
            if (message.hasSticker()) return addStickerToStickerSet(message, currentStickerSet, botAPI)
            val replyMessage = message.replyToMessage ?: return "Nothing to add"
            if (replyMessage.hasPhoto()) return addPhotoToStickerSet(
                replyMessage,
                currentStickerSet,
                emoji,
                botAPI
            )
            if (replyMessage.hasSticker()) return addStickerToStickerSet(replyMessage, currentStickerSet, botAPI)
            return "Nothing to add"
        } catch (e: Throwable) {
            if (message.chat.isUserChat) throw e
            return null
        }
    }
}

class AddCommand(imageProvider: ImageProvider): TextCommand("/add", "Add image or replied sticker to current sticker pack") {

    private val executor = AddExecutor(imageProvider)


    override fun execute(message: Message, botAPI: StickerBot): String? {
        return executor.execute(message, botAPI, false)
    }
}

class AddSpecialCommand(imageProvider: ImageProvider) : SpecialCommand(UserState.ADD,  "Add image or replied sticker to current sticker pack") {
    private val executor = AddExecutor(imageProvider)

    override fun execute(message: Message, botAPI: StickerBot): String? {
        return executor.execute(message, botAPI, true)
    }
}
