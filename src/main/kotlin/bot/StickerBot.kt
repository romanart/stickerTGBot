package bot

import Config
import ImageProvider
import MemeProvider
import command.*
import database.DatabaseConnection
import mu.KLogging
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException
import java.io.File
import java.sql.ResultSet
import java.util.*
import java.util.concurrent.PriorityBlockingQueue

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
        const val USER_DEFAULT_EMOJI_TABLE = "defaultUserEmojy"
        const val CHAT_WELCOME_MESSAGE_TABLE = "chatWelcomeMessage"
        const val CHAT_USER_CAPTION_TABLE = "chatUserCaption"
        const val STICKER_PACK_OWNER_TABLE = "stickerPackOwner"
        const val GROUP_STICKER_PACK_TABLE = "groupStickerPack"
        const val HOGWARTS_CHEAT_TABLE = "hogwartsCheat"
    }

    override fun onClosing() {
        dbConnection.releaseConnection()
        workingDirectory.deleteRecursively()
        super.onClosing()
    }

    private inner class DropCheatTableTask(time: Long) : DropTableTask(HOGWARTS_CHEAT_TABLE, dbConnection, time) {
        override fun postProcess() {
            schedCheatUpdateEvent()
        }
    }

    private inner class PingTable(time: Long): BotTask(time) {
        override fun execute(botApi: StickerBot) {
            dbConnection.executeQuery("SELECT 1") { /* nothing to do */ }
            schedDBPing()
        }
    }

    private fun schedDBPing() {
        pendingTaskQueue.add(PingTable(System.currentTimeMillis() + 5.hoursToMillis))
    }

    private fun schedCheatUpdateEvent() {
        pendingTaskQueue.add(DropCheatTableTask(System.currentTimeMillis() + 24.hoursToMillis))
    }

    private val pendingTaskQueue  = startCleanupDaemon()

    private fun startCleanupDaemon(): AbstractQueue<BotTask> {
        val queue = PriorityBlockingQueue<BotTask>(32)
        val worker = PendingTaskDaemon(queue, this)
        val workerThread = Thread(worker, "Pending worker thread").apply {
            isDaemon = true
        }
        workerThread.start()
        return queue
    }

    init {
        schedCheatUpdateEvent()
        schedDBPing()
    }

    override fun getBotUsername() = config.botName
    override fun getBotToken() = config.botToken

    private val ownerID = config.ownerID

    val botLogger get() = logger

    val textCommands = listOf(
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
        AddGroupCommand(imageProvider),
        SelectGroupCommand(),
        DefaultEmojy(),
        ResetCommand(),
        StopCommand(ownerID)
    )

    val actions = listOf(
        StartHogwartsAction(),
        PutHatAction(),
        CatchASnithAction(),
        CatchAPrihodAction(),
        HogwartsScoreAction(),
        HogwartsPersonalScoreAction(),
        NegotiateAction(),
        NotifyAction(ownerID),
        HogwartsPlayerList(),
        AnswerAction(),
        SetPuzzleAnswer(ownerID),
        SetPuzzleQuestion(ownerID),
        CheatersListAction(ownerID)
    )

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
                when {
                    messageText.startsWith("/") -> {
                        val command = textCommands.firstOrNull { messageText.startsWith("${it.name} ") || messageText == it.name }
                        if (command != null) {
                            command.execute(message, this)?.let { response ->
                                execute(SendMessage(message.chatId, response).also {
                                    it.replyToMessageId = message.messageId
                                })
                            }
                        } else reportUnknownCommand(messageText, message.chat.isUserChat, message.chatId)
                    }
                    messageText.startsWith("!") -> {
                        val action = actions.firstOrNull { it.checkAction(messageText) }
                        if (action != null) {
                            val response = action.execute(message, this)?.let { response ->
                                try {
                                    execute(SendMessage(message.chatId, response).also {
                                        it.replyToMessageId = message.messageId
                                    })
                                } catch (te: TelegramApiRequestException) {
                                    // If replying message was deleted try to send with out it
                                    execute(SendMessage(message.chatId, response))
                                }
                            }
                            if (action.cleanUp) {
                                val cleanupTime = System.currentTimeMillis() + 37.secsToMillis
                                if (response != null) {
                                    pendingTaskQueue.add(MessageCleanUpTask(message.chatId, response.messageId, cleanupTime))
                                }
                            }
                        }
                    }
                    message.chat.isUserChat -> {
                        val currentState = getUserState(message.from!!.id, message.chatId)
                        userSpecialCommand.firstOrNull { it.state == currentState }?.let { command ->
                            command.execute(message, this)?.let { response ->
                                execute(SendMessage(message.chatId, response).also {
                                    it.replyToMessageId = message.messageId
                                })
                            }
                        }
                    }
                    else -> {
                        // TODO: special actions like user leave or join the chat
                    }
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
            "SELECT emojy FROM $USER_DEFAULT_EMOJI_TABLE " +
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
            "INSERT INTO $USER_DEFAULT_EMOJI_TABLE (user_id, emojy) " +
            "VALUES ($chatId, '$newEmoji') ON DUPLICATE KEY " +
            "UPDATE emojy = '$newEmoji';"

        logger.info { "QUERY: $query" }

        dbConnection.executeUpdate(query)
    }

    fun getStickerPackOwner(stickerPackName: String): Int {
        val query =
            "SELECT owner_id FROM $STICKER_PACK_OWNER_TABLE " +
            "WHERE sticker_pack_id = '$stickerPackName'"

        logger.info { "QUERY: $query" }

        return dbConnection.executeQuery(query) { r ->
            if (r.next()) r.getInt(1) else -1
        }
    }

    fun setStickerPackOwner(stickerPackName: String, ownerId: Int) {
        val query =
            "INSERT INTO $STICKER_PACK_OWNER_TABLE (sticker_pack_id, owner_id) " +
             "VALUES ('$stickerPackName', $ownerId)"

        logger.info { "QUERY: $query" }

        dbConnection.executeUpdate(query)
    }

    fun getGroupStickerPack(chat_id: Long): String? {
        val query =
            "SELECT sticker_pack_name FROM $GROUP_STICKER_PACK_TABLE " +
            "WHERE chat_id = $chat_id"

        logger.info { "QUERY: $query" }

        return dbConnection.executeQuery(query) { r ->
            if (r.next()) r.getString(1) else null
        }
    }

    fun setGroupStickerPack(chat_id: Long, stickerPackName: String) {
        val query =
            "INSERT INTO $GROUP_STICKER_PACK_TABLE (chat_id, sticker_pack_name) " +
            "VALUES ($chat_id, '$stickerPackName') ON DUPLICATE KEY " +
            "UPDATE sticker_pack_name = '$stickerPackName';"

        logger.info { "QUERY: $query" }

        dbConnection.executeUpdate(query)
    }

    fun <R> executeQuery(query: String, handler: (ResultSet) -> R): R {

        logger.info { "QUERY: $query" }

        return dbConnection.executeQuery(query, handler)
    }

    fun executeUpdate(query: String) {

        logger.info { "QUERY: $query" }

        dbConnection.executeUpdate(query)
    }

}