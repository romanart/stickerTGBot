package command

import SessionState
import MemeProvider
import mu.KLogger
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.File
import org.telegram.telegrambots.meta.api.objects.Message

class MemeCommand(private val memeProvider: MemeProvider, logger: KLogger, botAPI: TelegramLongPollingBot) : Command<SessionState>("meme", logger, botAPI) {
    override val isChatCommand = true
    override val isGroupCommand = true

    override fun verifyArguments(tokens: List<String>) = tokens.size > 1

    override fun verifyState(state: SessionState) = true

    override fun process(message: Message, state: SessionState): SessionState {
        val tokens = tokenizeCommand(message).drop(1)

        logger.info { "Caption to meme: ${tokens.joinToString(" ")}" }

        val imageFileId = extractPhoto(message)

        val res1 = GetFile().setFileId(imageFileId)
        val response1 = (botAPI.execute(res1) as File).filePath

        val downloadImage = botAPI.downloadFile(response1)

        logger.info { "Build a meme with ${downloadImage.name}" }

        val memeFile = memeProvider.createMeme(downloadImage, tokens)

        botAPI.execute(SendPhoto().apply {
            setChatId(message.chatId)
            setPhoto(memeFile)
        })

        return SessionState.DEFAULT
    }

}