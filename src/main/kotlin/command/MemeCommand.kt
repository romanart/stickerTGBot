package command

import MemeProvider
import bot.StickerBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.File
import org.telegram.telegrambots.meta.api.objects.Message

class MemeCommand(private val memeProvider: MemeProvider) : TextCommand("/meme", "Create a meme from image or sticker") {
    override fun execute(message: Message, botAPI: StickerBot): String? {
        val tokens = message.tokenizeCommand().drop(1)

        val imageFileId = if (message.hasPhoto()) message.extractPhoto() else {
            val replyToMessage = message.replyToMessage ?: return null
            when {
                replyToMessage.hasPhoto() -> replyToMessage.extractPhoto()
                replyToMessage.hasSticker() -> replyToMessage.extractStickerPhoto()
                else -> return null
            }
        }

        assert(imageFileId != null)

        val res1 = GetFile().setFileId(imageFileId)
        val response1 = (botAPI.execute(res1) as File).filePath

        val downloadImage = botAPI.downloadFile(response1)

        botAPI.botLogger.info { "Build a meme with ${downloadImage.name}" }

        val memeFile = memeProvider.createMeme(downloadImage, tokens)

        botAPI.execute(SendPhoto().apply {
            setChatId(message.chatId)
            setPhoto(memeFile)
        })

        return null
    }
}