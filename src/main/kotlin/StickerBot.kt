import command.*
import database.DatabaseConnection
import mu.KLogging
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import java.io.File

class StickerBot(
    private val config: Config,
    private val imageProvider: ImageProvider,
    private val memeProvider: MemeProvider,
    private val dbConnection: DatabaseConnection,
    private val workingDirectory: File,
    botOptions: DefaultBotOptions
) : TelegramLongPollingBot(botOptions) {

    companion object : KLogging() {
        const val USER_STATE_TABLE = "userState"
        const val USER_CURRENT_STICKER_PACK_TABLE = "userCurrentStickerPack"
        const val USER_STICKER_PACKS_TABLE = "userStickerPacks"
        const val USER_DEFAULT_EMPJI_TABLE = "defaultUserEmojy"
        const val CHAT_WELCOME_MESSAGE_TABLE = "chatWelcomeMessage"
        const val CHAT_USER_CAPTION_TABLE = "chatUserCaption"
    }

    override fun onClosing() {
        dbConnection.releaseConnection()
        workingDirectory.deleteRecursively()
        super.onClosing()
    }

    override fun getBotUsername() = config.botName
    override fun getBotToken() = config.botToken

    private val ownerID = config.ownerID

    val botLogger get() = logger

    private val textBasedCommand = listOf(
        LogCommand(ownerID),
        DeleteCommand(),
        ConverterCommand(imageProvider),
        TossCommand(),
        HelpCommand("/help", false, ""),
        HelpCommand("/start", true, "(used when bot starts)"),
        SelectCommand(),
        AddCommand(imageProvider),
        MemeCommand(memeProvider),
        CreateNewStickerSet(imageProvider),
        DefaultEmojy(),
        ResetCommand(),
        StopCommand(ownerID)
    )

    val textCommands get() = textBasedCommand

    private val userSpecialCommand = listOf(
        SelectSpecialCommand(),
        AddSpecialCommand(imageProvider),
        ConvertSpecialCommand(UserState.CONVERT, imageProvider),
        ConvertSpecialCommand(UserState.EMPTY, imageProvider)
    )

    fun isMyOwnSticker(stickerPackName: String) = stickerPackName.endsWith("_by_${botUsername}")

    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage()) {
            val message = update.message
            if (message.hasText() && message.text.startsWith("/")) logger.info {
                "from: ${message.chatId}, text: ${message.text ?: "<empty>"}"
            }
            val messageText = message.text ?: message.caption ?: ""
            try {
                if (messageText.startsWith("/")) {
                    val command = textBasedCommand.firstOrNull { messageText.startsWith("${it.name} ") || messageText == it.name }
                    if (command != null) {
                        command.execute(message, this)?.let { response ->
                            execute(SendMessage(message.chatId, response))
                        }
                    } else reportUnknownCommand(messageText, message.chat.isUserChat, message.chatId)
                } else if (message.chat.isUserChat) {
                    val currentState = getUserState(message.from!!.id, message.chatId)
                    userSpecialCommand.firstOrNull { it.state == currentState }?.let { command ->
                        command.execute(message, this)?.let { response ->
                            execute(SendMessage(message.chatId, response))
                        }
                    }
                } else {
                    // TODO: special actions like user leave on join the chat
                }
            } catch (ex: Exception) {
                logger.warn { ex.toString() }
                logger.warn { ex.stackTrace.joinToString(separator = "\n") }
            }
        }
    }

    private fun reportUnknownCommand(messageText: String, isUserChar: Boolean, chatId: Long) {
        val response = "Unknown command [$messageText]"
        logger.info { response }
        if (isUserChar) {
            execute(SendMessage(chatId, response))
        }
    }

    // TODO: move to some sql builder instead of manually created queries
    fun setCurrentStickerPack(userId: Int, chatId: Long, setName: String) {
        val query =
            "INSERT INTO $USER_CURRENT_STICKER_PACK_TABLE (user_id, chat_id, sticker_pack_id) " +
            "VALUES ($userId, $chatId, '$setName') ON DUPLICATE KEY " +
            "UPDATE sticker_pack_id = '$setName';"

        logger.info { "QUERY: $query" }

        dbConnection.executeUpdate(query)
    }

    fun getCurrentStickerSet(userId: Int, chatId: Long): String? {
        val query =
            "SELECT sticker_pack_id FROM $USER_CURRENT_STICKER_PACK_TABLE " +
            "WHERE user_id = $userId AND chat_id = $chatId;"

        logger.info { "QUERY: $query" }

        return dbConnection.executeQuery(query) { r ->
            if (r.next()) r.getString(1) else null
        }
    }

    fun getDefaultEmojy(chatId: Long): String? {
        val query =
            "SELECT emojy FROM $USER_DEFAULT_EMPJI_TABLE " +
            "WHERE user_id = $chatId;"

        logger.info { "QUERY: $query" }

        return dbConnection.executeQuery(query) { r ->
            if (r.next()) r.getString(1) else null
        }
    }

    fun getUserState(userId: Int, chatId: Long): UserState {
        val query =
            "SELECT state_id FROM $USER_STATE_TABLE " +
            "WHERE user_id = $userId AND chat_id = $chatId;"

        logger.info { "QUERY: $query" }

        val stateId =  dbConnection.executeQuery(query) { r ->
            if (r.next()) r.getInt(1) else 0
        }
        return UserState.values()[stateId]
    }

    fun setUserState(userId: Int, chatId: Long, state: UserState) {
        val stateId = state.ordinal
        val query =
            "INSERT INTO $USER_STATE_TABLE (user_id, chat_id, state_id) " +
            "VALUES ($userId, $chatId, $stateId) ON DUPLICATE KEY " +
            "UPDATE state_id = $stateId;"

        logger.info { "QUERY: $query" }

        dbConnection.executeUpdate(query)
    }

    fun setDefaultEmoji(chatId: Long, newEmoji: String) {
        val query =
            "INSERT INTO $USER_DEFAULT_EMPJI_TABLE (user_id, emojy) " +
            "VALUES ($chatId, '$newEmoji') ON DUPLICATE KEY " +
            "UPDATE emojy = '$newEmoji';"

        logger.info { "QUERY: $query" }

        dbConnection.executeUpdate(query)
    }
}