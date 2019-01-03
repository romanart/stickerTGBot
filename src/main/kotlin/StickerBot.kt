import mu.KLogging
import mu.KotlinLogging
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.stickers.AddStickerToSet
import org.telegram.telegrambots.meta.api.methods.stickers.CreateNewStickerSet
import org.telegram.telegrambots.meta.api.methods.stickers.GetStickerSet
import org.telegram.telegrambots.meta.api.objects.File
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.stickers.MaskPosition
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker
import org.telegram.telegrambots.meta.api.objects.stickers.StickerSet
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.lang.Exception
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class RomanTestFirstBot(private val config: Config, private val imageProvider: ImageProvider): TelegramLongPollingBot() {

    companion object: KLogging()

    sealed class SessionState {
        object Converter: SessionState()
        object Select : SessionState()
        class Creation(val name: String, val title: String): SessionState()
        class Clone(val name: String, val title: String): SessionState()
        class AddSticker(val stickerPackName: String): SessionState()
        companion object {
            val DEFAULT = Converter
        }
    }

    private val stateMap = ConcurrentHashMap<Long, SessionState>()

    override fun getBotUsername() = config.botName
    override fun getBotToken() = config.botToken

    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage()) {
            val message = update.message
            val chat = message.chat

            if (message.chat.isGroupChat || message.chat.isSuperGroupChat) return


            logger.info { "Message from ${chat.userName} (${chat.firstName} ${chat.lastName}, @${chat.id}, @${chat.id.toInt()})" }

            try {
                when {
                    message.hasText() -> processMessageCommand(message)
                    message.hasSticker() -> processStickerMessage(message)
                    message.hasPhoto() -> processPhotoMessage(message)
                }
            } catch (ex: Exception) {
                logger.error(ex) { "Error handling $message" }
                execute(SendMessage(chat.id, "Error: ${ex.message ?: "unknown error"}"))
            }
        }
    }

    private fun processStickerMessage(message: Message) {
        assert(message.hasSticker())
        val sticker = message.sticker
        val state = stateMap.getOrDefault(message.chatId, SessionState.DEFAULT)

        when (state) {
            is SessionState.Select -> {
                stateMap[message.chatId] = SessionState.AddSticker(sticker.setName)
                execute(SendMessage(message.chatId, "StickerPack ${sticker.setName} is chosen, send me photo you would like too to it"))
            }
            is SessionState.Clone -> {
                val stickerSetReq = GetStickerSet(sticker.setName)
                val stickerSet = execute(stickerSetReq) as StickerSet
                stateMap[message.chatId] = SessionState.Creation(state.name, state.title)

                for (sticker in stickerSet.stickers) {
                    copySticker(sticker, stateMap[message.chatId]!!, message.chatId)
                }
            }
            else -> {
                copySticker(sticker, state, message.chatId)
            }
        }
    }

    private fun copySticker(sticker: Sticker, state: SessionState, chatId: Long) {
        val fileId = sticker.fileId
        val res = GetFile().setFileId(fileId)
        val fileResponse = execute(res) as File
        val image = downloadFile(fileResponse)
        sendImage(state, image, chatId, sticker.emoji, null)
    }

    private fun processPhotoMessage(message: Message) {
        assert(message.hasPhoto())
        val state = stateMap.getOrDefault(message.chatId, SessionState.DEFAULT)

        val photo = message.photo.last()
        val res = GetFile().setFileId(photo.fileId)
        val response2 = execute(res) as File

        val file = downloadFile(response2)

        val convertedImage = imageProvider.getImageFile(file)

        sendImage(state, convertedImage, message.chatId, toEmodji(message.caption),  null)
    }

    private fun sendImage(state: SessionState, convertedImage: java.io.File, chatId: Long, emodji: String, mask: MaskPosition?) {
        val message = when (state) {
            is SessionState.Converter -> {
                execute(SendDocument().setChatId(chatId).setDocument(convertedImage))
                "Image is converted"
            }
            is SessionState.AddSticker -> {
                execute(AddStickerToSet(chatId.toInt(), state.stickerPackName, emodji).setPngSticker(convertedImage))
                "Add new sticker to ${state.stickerPackName.toStickerURL}"
            }
            is SessionState.Creation -> {
                stateMap[chatId] = SessionState.AddSticker(state.name)
                execute(CreateNewStickerSet(chatId.toInt(), state.name, state.title, emodji).setPngStickerFile(convertedImage).also {
                    it.containsMasks = mask != null
                    it.maskPosition = mask
                })
                "Your new sticker set is created and available by link ${state.name.toStickerURL}"
            }
            else -> { "Unknown Command" }
        }
        execute(SendMessage(chatId, message))
    }


    private val verificationRegex = Pattern.compile("^[A-Za-z][\\w\\d_]+[\\w\\d]$")

    private fun verifyStickerID(name: String) {
        if (!verificationRegex.matcher(name).matches()) {
            throw Exception("'$name' is not applicable as sticker set ID")
        }
    }

    private val emodjiPattern = Pattern.compile("[\\u20a0-\\u32ff\\ud83c\\udc00-\\ud83d\\udeff\\udbb9\\udce5-\\udbb9\\udcee]")

    private fun toEmodji(s: String?) = s?.let { if (checkEmodji(it)) it else "☺️" } ?: "☺️"
    private fun checkEmodji(emodji: String) = emodjiPattern.matcher(emodji).matches()

    private val String.toStickerURL get() = "https://t.me/addstickers/${this}"
    private val String.stickerPackName get() = "${this}_by_$botUsername"

    private fun processMessageCommand(message: Message) {
        assert(message.hasText())
        val text = message.text

        logger.info { "Command: $text" }

        val response = when {
            text.startsWith("/create") || text.startsWith("/clone") -> {
                val tokens = text.split(" ")
                if (tokens[0] != "/create" && tokens[0] != "/clone") {
                    logger.warn { "Unknown command $text" }
                    return
                }
                when (tokens.size) {
                    1 -> SendMessage(message.chatId, "Please provide <name> and <title> for you sticker pack")
                    2 -> SendMessage(message.chatId, "Please provide <title> for you sticker pack")
                    else -> {
                        val name = tokens[1]
                        verifyStickerID(name)

                        val title = tokens.drop(2).joinToString(" ")

                        stateMap[message.chatId] =
                                if (tokens[0] == "/create") SessionState.Creation(name.stickerPackName, title) else
                                    SessionState.Clone(name.stickerPackName, title)
                        SendMessage(message.chatId, "Now send me the first photo for your new sticker set")
                    }
                }
            }
            text.startsWith("/convert") -> {
                stateMap[message.chatId] = SessionState.Converter
                SendMessage(message.chatId, "Now send me sticker from set you want to add new one")
            }
            text.startsWith("/select") -> {
                stateMap[message.chatId] = SessionState.Select
                SendMessage(message.chatId, "Now send me sticker from set you want to add new one")
            }
            text.startsWith("/help") || text.startsWith("/start") -> {
                SendMessage(message.chatId, HELP_STRING)
            }
            else -> {
                logger.warn { "Unknown command $text" }
                null
            }
        }

        response?.let { execute(it) }
    }

    private val HELP_STRING = """
        /help - show this message
        /create <stickerset_id> <title> - creates new sticker pack at the link https://t.me/addstickers/<stickerset_id>_by_<botname>
        /convert - send a photo and I resize and convert it into png
        /select - send me a sticker from set and I will try add a new stickers there
        /clone - first send me  <stickerset_id> <title> like for /create and in the next message send sticker from set you want to clone
    """.trimIndent()

}