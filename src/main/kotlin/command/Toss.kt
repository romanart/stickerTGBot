package command

import mu.KLogger
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import SessionState
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import java.security.SecureRandom

class TossCommand(logger: KLogger, botAPI: TelegramLongPollingBot) : Command<SessionState>("toss", logger, botAPI) {
    override fun verifyArguments(tokens: List<String>) = true

    override fun verifyState(state: SessionState) = true

    private val random = SecureRandom.getInstance("SHA1PRNG").also { it.setSeed(System.currentTimeMillis()) }

    override fun process(message: Message, state: SessionState): SessionState {

        val seed = random.nextInt(100000)

        val result = when {
            seed == 0 -> "A coin fell on the edge"
            seed == 99999 -> "A coin hung in the air"
            seed > 49999 -> "Tail"
            else -> "Head"
        }

        botAPI.execute(SendMessage(message.chatId, result).also { it.replyToMessageId = message.messageId })
        return state
    }

    override val isChatCommand = true
    override val isGroupCommand = true
}