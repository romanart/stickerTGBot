package command

import mu.KLogger
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import SessionState
import org.telegram.telegrambots.meta.api.methods.stickers.DeleteStickerFromSet

class DeleteCommand(logger: KLogger, botAPI: TelegramLongPollingBot):
    Command<SessionState>("delete", logger, botAPI) {
    override val isChatCommand = true
    override val isGroupCommand = false

    override fun verifyArguments(tokens: List<String>) = tokens.size  == 1

    override fun verifyState(state: SessionState) = true

    override fun verifyContent(message: Message) = true

    override fun process(message: Message, state: SessionState): SessionState {
        logger.info { "Deleting sticker..." }
        botAPI.execute(SendMessage(message.chatId, "Now send me sticker you'd like to delete"))
        return SessionState.Delete
    }
}

class DoDeleteCommand(logger: KLogger, botAPI: TelegramLongPollingBot):
    Command<SessionState.Delete>("<delete>", logger, botAPI) {
    override val isChatCommand = true
    override val isGroupCommand = false

    override fun verifyArguments(tokens: List<String>) = tokens.isEmpty()

    override fun verifyState(state: SessionState) = state === SessionState.Delete

    override fun verifyContent(message: Message) = message.hasSticker() && message.sticker.setName.endsWith("_by_$botName")

    override fun verifyCommand(command: String) = true

    override fun process(message: Message, state: SessionState.Delete): SessionState {
        require(message.hasSticker())
        val sticker = message.sticker
        logger.info { "Delete sticker from set ${sticker.setName}" }
        botAPI.execute(DeleteStickerFromSet(sticker.fileId))
        botAPI.execute(SendMessage(message.chatId, "Sticker has been deleted from set ${sticker.setName}"))
        return SessionState.Converter
    }
}