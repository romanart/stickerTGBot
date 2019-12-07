import command.*
import mu.KLogging
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import java.util.concurrent.ConcurrentHashMap

sealed class SessionState {
    object Converter: SessionState()
    object Select : SessionState()
    object Delete : SessionState()
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
        DeleteCommand(logger, this),
        DoDeleteCommand(logger, this),
        MemeCommand(memeProvider, logger, this),
        TossCommand(logger, this),
        HelpCommand("help"),
        HelpCommand("start")
    )

    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage()) {
            val message = update.message
            if (message.hasText() && message.text.startsWith("/")) logger.info { "from: ${message.chatId}, text: ${message.text ?: "<empty>"}" }
            val state = stateMap[message.chatId] ?: SessionState.DEFAULT
            try {
                commands.firstOrNull { it.checkCommand(message, state) }?.let {
                    @Suppress("UNCHECKED_CAST")
                    (it as Command<SessionState>).process(
                        message,
                        state
                    )
                }?.let {
                    stateMap[message.chatId] = it
                } ?: reportUnknownCommand(message)
            } catch(ex: Exception) {
                logger.warn { ex.toString() }
                logger.warn { ex.stackTrace.joinToString(separator = "\n") }
            }
        }
    }

    private fun reportUnknownCommand(message: Message) {
        if (message.chat.isUserChat) {
            val text = message.text ?: ""
            val response = "Unknown command [$text]"
            logger.info { response }
            execute(SendMessage(message.chatId, response))
        }
    }

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
        /delete - send me a sticker you would like to delete
        /clone - first send me <stickerset_id> <title> like for /create and in the next message send sticker from set you want to clone
        /toss - Flip a coin
    """.trimIndent()
    }
}