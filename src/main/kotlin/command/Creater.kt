package command

import mu.KLogger
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import SessionState
import ImageProvider
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.stickers.AddStickerToSet
import org.telegram.telegrambots.meta.api.methods.stickers.CreateNewStickerSet
import org.telegram.telegrambots.meta.api.methods.stickers.GetStickerSet
import org.telegram.telegrambots.meta.api.objects.File
import org.telegram.telegrambots.meta.api.objects.stickers.StickerSet
import java.util.regex.Pattern

abstract class ChoseNewStickerSet(commandName: String, logger: KLogger, botAPI: TelegramLongPollingBot) : Command<SessionState>(commandName, logger, botAPI) {
    final override val isChatCommand = true
    final override val isGroupCommand = false

    private val verificationPattern = Pattern.compile("^[A-Za-z][\\w\\d_]*$")

    private fun verifyStickerID(name: String): Boolean {
        if (!verificationPattern.matcher(name).matches()) {
            throw Exception("'$name' is not applicable as sticker set ID\nMake sure your ID starts with english character and contains only alphabetic characters, digits or '_' symbol")
        }
        return true
    }

    final override fun verifyArguments(tokens: List<String>) = tokens.size > 2 && verifyStickerID(tokens[1])

    final override fun verifyState(state: SessionState) = true

    protected abstract val response: String

    protected abstract fun buildState(tokens: List<String>): SessionState

    final override fun process(message: Message, state: SessionState): SessionState {
        logger.info { response }
        val tokens = tokenizeCommand(message)
        botAPI.execute(SendMessage(message.chatId, response))
        return buildState(tokens)
    }
}

class ChoseCreate(logger: KLogger, botAPI: TelegramLongPollingBot): ChoseNewStickerSet("create", logger, botAPI) {
    override val response = "Now send me the first photo for your new sticker set"
    override fun buildState(tokens: List<String>) = SessionState.Creation(tokens[1].stickerPackName(botName), tokens.drop(2).joinToString(" "))
}

class ChoseCloner(logger: KLogger, botAPI: TelegramLongPollingBot): ChoseNewStickerSet("clone", logger, botAPI) {
    override val response = "Now send me sticker form set you would like to clone"
    override fun buildState(tokens: List<String>) = SessionState.Clone(tokens[1].stickerPackName(botName), tokens.drop(2).joinToString(" "))
}

class DoCreate(private val imageProvider: ImageProvider, logger: KLogger, botAPI: TelegramLongPollingBot): Command<SessionState.Creation>("<create>", logger, botAPI) {
    override val isChatCommand = true
    override val isGroupCommand = false

    override fun verifyArguments(tokens: List<String>) = tokens.size < 2

    override fun verifyState(state: SessionState) = state is SessionState.Creation

    override fun verifyCommand(command: String) = true

    override fun verifyContent(message: Message) = message.run { hasPhoto() }

    override fun process(message: Message, state: SessionState.Creation): SessionState {
        logger.info { "Creating sticker set..." }

        val photo = message.photo.last()
        val res = GetFile().setFileId(photo.fileId)
        val response2 = botAPI.execute(res) as File

        val file = botAPI.downloadFile(response2)

        val convertedImage = imageProvider.getImageFile(file)

        val emodji = tokenizeCommand(message).singleOrNull()

        val result = SessionState.AddSticker(state.name)
        botAPI.execute(CreateNewStickerSet(message.chatId.toInt(), state.name, state.title, emodji).setPngStickerFile(convertedImage).also {
            it.containsMasks = false
        })
        val response = "Your new sticker set is created and available by link ${state.name.toStickerURL}"

        logger.info { response }

        botAPI.execute(SendMessage(message.chatId, response))

        return result
    }
}

class DoClone(logger: KLogger, botAPI: TelegramLongPollingBot): Command<SessionState.Clone>("<clone>", logger, botAPI) {
    override val isChatCommand = true
    override val isGroupCommand = false

    override fun verifyArguments(tokens: List<String>) = tokens.isEmpty()

    override fun verifyState(state: SessionState) = state is SessionState.Clone

    override fun verifyCommand(command: String) = true

    override fun process(message: Message, state: SessionState.Clone): SessionState {
        val messageSticker = message.sticker
        logger.info { "Cloning sticker set ${messageSticker.setName} into ${state.name}" }
        val stickerSetReq = GetStickerSet(messageSticker.setName)
        val stickerSet = botAPI.execute(stickerSetReq) as StickerSet
        val newSetName = state.name
        val chatId = message.chatId
        var created = false

        for (sticker in stickerSet.stickers) {
            val fileId = sticker.fileId
            val res = GetFile().setFileId(fileId)
            val fileResponse = botAPI.execute(res) as File
            val image = botAPI.downloadFile(fileResponse)
            if (created) {
                botAPI.execute(AddStickerToSet(chatId.toInt(), newSetName, sticker.emoji).setPngSticker(image).apply {
                    //                maskPosition = sticker.maskPosition
                })
            } else {
                created = true
                botAPI.execute(CreateNewStickerSet(chatId.toInt(), state.name, state.title, sticker.emoji).setPngStickerFile(image).also {
                    it.containsMasks = false
                    it.maskPosition = null
                })
            }
            botAPI.execute(SendMessage(chatId, "Creating new sticker set ${newSetName.toStickerURL}".also { logger.info { it } }))
        }

        return SessionState.AddSticker(state.name)
    }

}