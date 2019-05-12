package command

import mu.KLogger
import ImageProvider
import SessionState
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.File
import org.telegram.telegrambots.meta.api.objects.Message


class ChoseConvert(logger: KLogger, botAPI: TelegramLongPollingBot): Command<SessionState>("convert", logger, botAPI) {
    override val isChatCommand = true
    override val isGroupCommand = false

    override fun verifyArguments(tokens: List<String>) = tokens.size == 1

    override fun verifyState(state: SessionState) = true

    private val response = "Now send me either photo or sticker you want to convert"

    override fun process(message: Message, state: SessionState): SessionState {
        logger.info { response }
        botAPI.execute(SendMessage(message.chatId, response))
        return SessionState.Converter
    }
}

class DoConvertCommand(private val imageProvider: ImageProvider, logger: KLogger, botAPI: TelegramLongPollingBot): Command<SessionState>("<convert>", logger, botAPI) {
    override fun verifyState(state: SessionState) = state == SessionState.Converter
    override val isChatCommand = true
    override val isGroupCommand = false

    override fun verifyContent(message: Message) = message.run { hasPhoto() || hasDocument() || hasSticker() }
    override fun verifyArguments(tokens: List<String>) = true
    override fun verifyCommand(command: String) = true

    private val response = "Image is converted"

    private fun extractSticker(message: Message): java.io.File {
        require(message.hasSticker())
        val sticker = message.sticker
        val fileId = sticker.fileId

        val res = GetFile().setFileId(fileId)
        val fileResponse = botAPI.execute(res) as File
        return botAPI.downloadFile(fileResponse)
    }

    override fun process(message: Message, state: SessionState) : SessionState {
        val convertedPhoto = if (message.hasSticker()) extractSticker(message) else {
            val photoId = extractPhoto(message)
            val file = botAPI.execute(GetFile().setFileId(photoId)) as File
            val downloadedFile = botAPI.downloadFile(file)
            imageProvider.getImageFile(downloadedFile)
        }

        botAPI.execute(SendDocument().setChatId(message.chatId).setDocument(convertedPhoto))
        logger.info { response }
        botAPI.execute(SendMessage(message.chatId, response))
        return SessionState.Converter
    }
}
