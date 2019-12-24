package command

import ImageProvider
import bot.StickerBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.objects.File
import org.telegram.telegrambots.meta.api.objects.Message

private class ConvertExecutor(private val imageProvider: ImageProvider) {

    private fun extractSticker(message: Message, botAPI: StickerBot): java.io.File {
        val sticker = message.sticker
        val fileId = sticker.fileId

        val res = GetFile().setFileId(fileId)
        val fileResponse = botAPI.execute(res) as File
        return botAPI.downloadFile(fileResponse)
    }

    private val response = "Image is converted"

    fun execute(message: Message, botAPI: StickerBot): String? {
        val convertedPhoto = convertMessagePhoto(message, botAPI) ?: message.replyToMessage?.let {
            convertMessagePhoto(it, botAPI)
        }

        return if (convertedPhoto != null) {
            botAPI.execute(SendDocument().setChatId(message.chatId).setDocument(convertedPhoto))
            botAPI.botLogger.info { response }
            response
        } else {
            if (message.chat.isUserChat) {
                botAPI.setUserState(message.from.id, message.chatId, UserState.CONVERT)
                null
            } else {
                "Nothing to convert"
            }
        }
    }

    private fun convertMessagePhoto(
        message: Message,
        botAPI: StickerBot
    ): java.io.File? {
        return when {
            message.hasSticker() -> extractSticker(message, botAPI)
            message.hasPhoto() -> {
                val photoId = message.extractPhoto()
                val file = botAPI.execute(GetFile().setFileId(photoId)) as File
                val downloadedFile = botAPI.downloadFile(file)
                imageProvider.getImageFile(downloadedFile)
            }
            else -> null
        }
    }
}

class ConverterCommand(imageProvider: ImageProvider): TextCommand("/convert", "Convert image or sticker into PNG") {
    private val executor = ConvertExecutor(imageProvider)

    override fun execute(message: Message, botAPI: StickerBot): String? {

        if (message.chat.isUserChat) {
            if (!message.hasPhoto() && message.replyToMessage == null) {
                botAPI.setUserState(message.from.id, message.chatId, UserState.CONVERT)
                return null
            }
        }

        return executor.execute(message, botAPI)
    }
}

class ConvertSpecialCommand(state: UserState, imageProvider: ImageProvider): SpecialCommand(state, "Make bot work in image-converting mode") {
    private val executor = ConvertExecutor(imageProvider)
    override fun execute(message: Message, botAPI: StickerBot): String? {
        return executor.execute(message, botAPI)
    }
}

