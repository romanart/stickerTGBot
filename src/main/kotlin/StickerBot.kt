import mu.KLogging
import mu.KotlinLogging
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.stickers.AddStickerToSet
import org.telegram.telegrambots.meta.api.methods.stickers.CreateNewStickerSet
import org.telegram.telegrambots.meta.api.objects.File
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.util.concurrent.ConcurrentHashMap

class RomanTestFirstBot(private val config: Config, private val imageProvider: ImageProvider): TelegramLongPollingBot() {

    companion object: KLogging()

    sealed class SessionState {
        object Converter: SessionState()
        object Select : SessionState()
        class Creation(val name: String, val title: String): SessionState()
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
            } catch (ex: TelegramApiException) {
                logger.error(ex) { "Error handling $message" }
                execute(SendMessage(chat.id, "Error: ${ex.message ?: "unknown error"}"))
            }
        }
    }

    private fun processStickerMessage(message: Message) {
        assert(message.hasSticker())
        val sticker = message.sticker
        val state = stateMap.getOrDefault(message.chatId, SessionState.DEFAULT)

        if (state is SessionState.Select) {
            stateMap[message.chatId] = SessionState.AddSticker(sticker.setName)
            execute(SendMessage(message.chatId, "StickerPack ${sticker.setName} is chosen, send me photo you would like too to it"))
        } else {
            val fileId = sticker.fileId
            val res = GetFile().setFileId(fileId)
            val fileResponse = execute(res) as File
            val image = downloadFile(fileResponse)
            sendImage(state, image, message.chatId, sticker.emoji)
        }
    }

    private fun processPhotoMessage(message: Message) {
        assert(message.hasPhoto())
        val state = stateMap.getOrDefault(message.chatId, SessionState.DEFAULT)

        val photo = message.photo.last()
        val res = GetFile().setFileId(photo.fileId)
        val response2 = execute(res) as File

        val file = downloadFile(response2)

        val convertedImage = imageProvider.getImageFile(file)

        sendImage(state, convertedImage, message.chatId, message.caption ?: "☺️")
    }

    private fun sendImage(state: SessionState, convertedImage: java.io.File, chatId: Long, emodji: String) {
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
                    it.containsMasks = false
                })
                "Your new sticker set is created and available by link ${state.name.toStickerURL}"
            }
            else -> { "Unknown Command" }
        }
        execute(SendMessage(chatId, message))
    }

    private val String.toStickerURL get() = "https://t.me/addstickers/${this}"
    private val String.stickerPackName get() = "${this}_by_$botUsername"

    private fun processMessageCommand(message: Message) {
        assert(message.hasText())
        val text = message.text

        logger.info { "Command: $text" }

        val response = when {
            text.startsWith("/create") -> {
                val tokens = text.split(" ")
                when (tokens.size) {
                    1 -> SendMessage(message.chatId, "Please provide <name> and <title> for you sticker pack")
                    2 -> SendMessage(message.chatId, "Please provide <title> for you sticker pack")
                    else -> {
                        val name = tokens[1]

                        val title = tokens.drop(2).joinToString(" ")

                        stateMap[message.chatId] = SessionState.Creation(name.stickerPackName, title)
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
    """.trimIndent()

}