package command

import ImageProvider
import SessionState
import mu.KLogger
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.stickers.AddStickerToSet
import org.telegram.telegrambots.meta.api.objects.File
import org.telegram.telegrambots.meta.api.objects.Message

class SelectCommand(logger: KLogger, botAPI: TelegramLongPollingBot):
        Command<SessionState>("select", logger, botAPI) {
    override val isChatCommand = true
    override val isGroupCommand = false

    override fun verifyArguments(tokens: List<String>) = tokens.size  == 1

    override fun verifyState(state: SessionState) = true

    override fun verifyContent(message: Message) = true

    override fun process(message: Message, state: SessionState): SessionState {
        logger.info { "Selecting sticker set..." }
        botAPI.execute(SendMessage(message.chatId, "Now send me sticker from set you want to add new one"))
        return SessionState.Select
    }
}

class DoSelectCommand(logger: KLogger, botAPI: TelegramLongPollingBot):
    Command<SessionState.Select>("<select>", logger, botAPI) {
    override val isChatCommand = true
    override val isGroupCommand = false

    override fun verifyArguments(tokens: List<String>) = tokens.isEmpty()

    override fun verifyState(state: SessionState) = state === SessionState.Select

    override fun verifyContent(message: Message) = message.hasSticker() && message.sticker.setName.endsWith("_by_$botName")

    override fun verifyCommand(command: String) = true

    override fun process(message: Message, state: SessionState.Select): SessionState {
        val stickerSet = message.sticker
        logger.info { "Selected sticker pack ${stickerSet.setName}" }
        botAPI.execute(SendMessage(message.chatId, "StickerPack ${stickerSet.setName} is chosen, send me photo you would like too to it"))
        return SessionState.AddSticker(stickerSet.setName)
    }
}

class DoAddCommand(private val imageProvider: ImageProvider, logger: KLogger, botAPI: TelegramLongPollingBot): Command<SessionState.AddSticker>("<add>", logger, botAPI) {
    override val isChatCommand = true
    override val isGroupCommand = false

    override fun verifyArguments(tokens: List<String>) = tokens.singleOrNull()?.let { checkEmodji(it) } ?: true

    override fun verifyState(state: SessionState) = state is SessionState.AddSticker

    override fun verifyContent(message: Message) = message.run { hasSticker() || hasPhoto() || hasDocument() }

    override fun verifyCommand(command: String) = true

    override fun process(message: Message, state: SessionState.AddSticker): SessionState {
        if (message.hasSticker()) {
            val sticker = message.sticker
            logger.info { "Adding sticker into ${sticker.setName} by emoji ${sticker.emoji}" }
            val fileID = sticker.fileId
            val fileResponse = botAPI.execute(GetFile().setFileId(fileID)) as File
            val file = botAPI.downloadFile(fileResponse)
            botAPI.execute(AddStickerToSet(message.chatId.toInt(), state.stickerPackName, sticker.emoji).setPngSticker(file))
            val response = "Add new sticker to ${state.stickerPackName.toStickerURL}"
            logger.info { response }
            botAPI.execute(SendMessage(message.chatId, response))
        } else {
            val fileID = extractPhoto(message)
            val filePhoto = botAPI.execute(GetFile().setFileId(fileID)) as File

            val file = botAPI.downloadFile(filePhoto)

            val convertedImage = imageProvider.getImageFile(file)
            val emodji = toEmodji(message.caption ?: message.text!!)

            botAPI.execute(AddStickerToSet(message.chatId.toInt(), state.stickerPackName, emodji).setPngSticker(convertedImage))
            val response  = "Add new sticker to ${state.stickerPackName.toStickerURL}"
            logger.info { response }

            botAPI.execute(SendMessage(message.chatId, response))
        }
        return state
    }
}