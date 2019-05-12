import command.*
import mu.KLogging
import org.apache.commons.io.input.ReversedLinesFileReader
import org.apache.log4j.FileAppender
import org.apache.log4j.Logger
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.stickers.AddStickerToSet
import org.telegram.telegrambots.meta.api.methods.stickers.CreateNewStickerSet
import org.telegram.telegrambots.meta.api.methods.stickers.GetStickerSet
import org.telegram.telegrambots.meta.api.objects.Chat
import org.telegram.telegrambots.meta.api.objects.File
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.stickers.MaskPosition
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker
import org.telegram.telegrambots.meta.api.objects.stickers.StickerSet
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.math.min

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


class StickerBot(private val config: Config, private val imageProvider: ImageProvider, private val memeProvider: MemeProvider): TelegramLongPollingBot() {

    companion object: KLogging()

    private val stateMap = ConcurrentHashMap<Long, SessionState>()

//    private fun userTitle(chat: Chat): String {
//        val sb = StringBuilder()
//
//        chat.userName?.let { sb.append(it) } ?: chat.firstName?.let {
//            sb.append(it)
//            chat.lastName?.let { sb.append(" $it") }
//        }
//
//        sb.append('@')
//        sb.append(chat.id)
//        return sb.toString()
//    }

    override fun getBotUsername() = config.botName
    override fun getBotToken() = config.botToken

    private val ownerID = config.ownerID

    private val commands = listOf(
        LogCommand(ownerID, logger, this),
        SelectCommand(logger, this),
        DoSelectCommand(logger, this),
        DoAddCommand(imageProvider, logger, this),
        ChoseConvert(logger, this),
        DoConvertCommand(imageProvider, logger, this),
        ChoseCreate(logger, this),
        DoCreate(imageProvider, logger, this),
        ChoseCloner(logger, this),
        DoClone(logger, this),
        MemeCommand(memeProvider, logger, this),
        HelpCommand("help"),
        HelpCommand("start")
    )

//    private fun processCommand(message: Message, processor: (Message)-> SessionState?) {
//        if (message.chat.run { isUserChat }) logger.info { "Message from ${userTitle(message.chat)}" }
//
//        try {
//            processor(message)?.let { stateMap[message.chatId] = it }
//        } catch (ex: Exception) {
//            logger.error(ex) { "Error handling $message" }
//            execute(SendMessage(message.chat.id, "Error: ${ex.message ?: "unknown error"}"))
//        }
//    }

//    override fun onUpdateReceived(update: Update) {
//        if (update.hasMessage()) {
//            val message = update.message
//
//            when {
//                message.chat.run { isGroupChat || isSuperGroupChat } -> processCommand(message) { processGroupMessage(it); null }
//                message.hasText() -> processCommand(message) { processMessageCommand(it); null }
//                message.hasSticker() -> processCommand(message) { processStickerMessage(it); null }
//                message.hasPhoto() -> processCommand(message) { processPhotoMessage(it); null }
//            }
//        }
//    }

    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage()) {
            val message = update.message
            logger.info { "from: ${message.chatId}, text: ${message.text ?: "<empty>"}" }
            val state = stateMap[message.chatId] ?: SessionState.DEFAULT
            commands.firstOrNull { it.checkCommand(message, state) }?.let { (it as Command<SessionState>).process(message, state) }?.let {
                stateMap[message.chatId] = it
            } ?: reportUnknownCommand(message)
        }
    }

    private fun reportUnknownCommand(message: Message) {
        val text = message.text ?: ""
        val response = "Unknown command [$text]"
        logger.info { response }
        execute(SendMessage(message.chatId, response))
    }


//    private fun extractPhoto(message: Message): String {
//        fun extractPhotoImpl(msg: Message): String? = msg.photo?.last()?.fileId ?: msg.document?.fileId
//
//        return extractPhotoImpl(message) ?: message.replyToMessage?.let { extractPhotoImpl(it) }
//        ?: throw java.lang.Exception("Please provide an image for /meme command")
//    }

//    private fun processGroupMessage(message: Message) {
//        val text = message.text ?: message.caption ?: return
//
//        val tokens = text.split(" ")
//
//        if (tokens.size == 1) return
//
//        val command = tokens[0]
//
//        if (command != "/meme" && command != "/meme@$botUsername") return
//
//        val caption = tokens.drop(1).joinToString(" ")
//
//        logger.info { "Caption to meme: $caption" }
//
//        val imageFileId = extractPhoto(message)
//
//        val res1 = GetFile().setFileId(imageFileId)
//        val response1 = (execute(res1) as File).filePath
//
//        val downloadImage = downloadFile(response1)
//
//        logger.info { "Build a meme with ${downloadImage.name}" }
//
//        val memeFile = memeProvider.createMeme(downloadImage, tokens.drop(1))
//
//        execute(SendPhoto().apply {
//            setChatId(message.chatId)
//            setPhoto(memeFile)
//        })
//    }

//    private fun processStickerMessage(message: Message) {
//        assert(message.hasSticker())
//        val messageSticker = message.sticker
//        val state = stateMap.getOrDefault(message.chatId, SessionState.DEFAULT)
//
//        when (state) {
//            is SessionState.Select -> {
//                logger.info { "Selected sticker pack ${messageSticker.setName}" }
//                stateMap[message.chatId] = SessionState.AddSticker(messageSticker.setName)
//                execute(SendMessage(message.chatId, "StickerPack ${messageSticker.setName} is chosen, send me photo you would like too to it"))
//            }
//            is SessionState.Clone -> {
//                logger.info { "Cloning sticker pack ${messageSticker.setName} into ${state.name}" }
//                val stickerSetReq = GetStickerSet(messageSticker.setName)
//                val stickerSet = execute(stickerSetReq) as StickerSet
//                stateMap[message.chatId] = SessionState.Creation(state.name, state.title)
//
//                for (sticker in stickerSet.stickers) {
//                    copySticker(sticker, stateMap[message.chatId]!!, message.chatId)
//                }
//            }
//            else -> {
//                logger.info { "Adding sticker info ${messageSticker.setName} by emodji ${messageSticker.emoji}" }
//                copySticker(messageSticker, state, message.chatId)
//            }
//        }
//    }

//    private fun copySticker(sticker: Sticker, state: SessionState, chatId: Long) {
//        val fileId = sticker.fileId
//        val res = GetFile().setFileId(fileId)
//        val fileResponse = execute(res) as File
//        val image = downloadFile(fileResponse)
//        sendImage(state, image, chatId, sticker.emoji, null)
//    }

//    private fun processPhotoMessage(message: Message) {
//        assert(message.hasPhoto())
//        val state = stateMap.getOrDefault(message.chatId, SessionState.DEFAULT)
//
//        logger.info { "Adding photo..." }
//
//        val photo = message.photo.last()
//        val res = GetFile().setFileId(photo.fileId)
//        val response2 = execute(res) as File
//
//        val file = downloadFile(response2)
//
//        val convertedImage = imageProvider.getImageFile(file)
//
//        sendImage(state, convertedImage, message.chatId, toEmodji(message.caption),  null)
//    }

//    private fun sendImage(state: SessionState, convertedImage: java.io.File, chatId: Long, emodji: String, mask: MaskPosition?) {
//        val message = when (state) {
//            is SessionState.Converter -> {
//                execute(SendDocument().setChatId(chatId).setDocument(convertedImage))
//                "Image is converted".also { logger.info { it } }
//            }
//            is SessionState.AddSticker -> {
//                execute(AddStickerToSet(chatId.toInt(), state.stickerPackName, emodji).setPngSticker(convertedImage))
//                "Add new sticker to ${state.stickerPackName.toStickerURL}".also { logger.info { it } }
//            }
//            is SessionState.Creation -> {
//                stateMap[chatId] = SessionState.AddSticker(state.name)
//                execute(CreateNewStickerSet(chatId.toInt(), state.name, state.title, emodji).setPngStickerFile(convertedImage).also {
//                    it.containsMasks = mask != null
//                    it.maskPosition = mask
//                })
//                "Your new sticker set is created and available by link ${state.name.toStickerURL}".also { logger.info { it } }
//            }
//            else -> { "Unknown Commands" }
//        }
//        execute(SendMessage(chatId, message))
//    }

    private val defaultLogLineCount = 30
    private val messageSizeLimit = 4096

    private val verificationPattern = Pattern.compile("^[A-Za-z][\\w\\d_]*$")

    private fun verifyStickerID(name: String) {
        if (!verificationPattern.matcher(name).matches()) {
            throw Exception("'$name' is not applicable as sticker set ID\nMake sure your ID starts with english character and contains only alphabetic characters, digits or '_' symbol")
        }
    }

    private val emodjiPattern = Pattern.compile("[\\u20a0-\\u32ff\\ud83c\\udc00-\\ud83d\\udeff\\udbb9\\udce5-\\udbb9\\udcee]")

    private fun toEmodji(s: String?) = s?.let { if (checkEmodji(it)) it else "☺️" } ?: "☺️"
    private fun checkEmodji(emodji: String) = emodjiPattern.matcher(emodji).matches()

    private val String.toStickerURL get() = "https://t.me/addstickers/${this}"
    private val String.stickerPackName get() = "${this}_by_$botUsername"

//    private fun processMessageCommand(message: Message) {
//        assert(message.hasText())
//        val text = message.text
//
//        logger.info { "Commands: $text" }
//
//        val response = when {
//            text.startsWith("/create") || text.startsWith("/clone") -> {
//                val tokens = text.split(" ")
//                if (tokens[0] != "/create" && tokens[0] != "/clone") {
//                    logger.warn { "Unknown command $text" }
//                    return
//                }
//                when (tokens.size) {
//                    1 -> SendMessage(message.chatId, "Please provide <name> and <title> for you sticker pack")
//                    2 -> SendMessage(message.chatId, "Please provide <title> for you sticker pack")
//                    else -> {
//                        val name = tokens[1]
//                        verifyStickerID(name)
//
//                        val title = tokens.drop(2).joinToString(" ")
//
//                        stateMap[message.chatId] =
//                                if (tokens[0] == "/create") SessionState.Creation(name.stickerPackName, title) else
//                                    SessionState.Clone(name.stickerPackName, title)
//                        SendMessage(message.chatId, "Now send me the first photo for your new sticker set")
//                    }
//                }
//            }
//            text.startsWith("/convert") -> {
//                stateMap[message.chatId] = SessionState.Converter
//                SendMessage(message.chatId, "Now send me either photo or sticker you want to convert")
//            }
//            text.startsWith("/select") -> {
//                stateMap[message.chatId] = SessionState.Select
//                SendMessage(message.chatId, "Now send me sticker from set you want to add new one")
//            }
////            text.startsWith("/help") || text.startsWith("/start") -> {
////                SendMessage(message.chatId, HELP_STRING)
////            }
//            text.startsWith("/log") -> {
//                if (message.chatId == ownerID) {
//                    sendLastLog(message)
//                }
//                null
//            }
//            else -> {
//                logger.warn { "Unknown command $text" }
//                null
//            }
//        }
//
//        response?.let { execute(it) }
//    }

//    private fun sendLastLog(message: Message) {
//        val text = message.text
//        val tokens = text.split(" ")
//        var limit = min(
//            if (tokens.size > 1) {
//                tokens[1].toIntOrNull() ?: defaultLogLineCount
//            } else defaultLogLineCount, 1024
//        )
//        val appenders = Logger.getRootLogger().allAppenders
//        val fileAppender = appenders.nextElement() as FileAppender
//        val loggingFile = java.io.File(fileAppender.file)
//        val fileReader = ReversedLinesFileReader(
//            loggingFile,
//            fileAppender.encoding?.let { Charset.forName(it) } ?: Charset.defaultCharset())
//        val lines = arrayOfNulls<String>(limit)
//        for (i in 0 until limit) {
//            lines[limit - i - 1] = fileReader.readLine()
//        }
//        val sb = StringBuilder()
//        while (limit > 0) {
//            val newLine = lines[lines.size - limit--]!!
//            val appendSize = newLine.length + 1 // '\n' symbol
//            if (sb.length + appendSize >= messageSizeLimit) {
//                execute(SendMessage(message.chatId, sb.toString()))
//                sb.clear()
//            }
//            sb.append(newLine)
//            sb.append('\n')
//        }
//        if (sb.isNotEmpty()) {
//            execute(SendMessage(message.chatId, sb.toString()))
//        }
//        fileReader.close()
//    }


    inner class HelpCommand(command: String): Command<SessionState>(command, logger, this) {
        override val isChatCommand = true
        override val isGroupCommand = false

        override fun verifyArguments(tokens: List<String>) = true

        override fun verifyState(state: SessionState) = true

        override fun process(message: Message, state: SessionState) = state.also {
            execute(SendMessage(message.chatId, HELP_STRING))
        }

        private val HELP_STRING = """
        /help - show this message
        /create <stickerset_id> <title> - creates new sticker pack at the link https://t.me/addstickers/<stickerset_id>_by_<botname>, do not forget omit `< >` brackets
           - example: `/create my_new_stickerset_1 LUCKY CATS`
        /convert - send a photo and I resize and convert it into png
        /select - send me a sticker from set and I will try add a new stickers there
        /clone - first send me <stickerset_id> <title> like for /create and in the next message send sticker from set you want to clone
    """.trimIndent()
    }


}