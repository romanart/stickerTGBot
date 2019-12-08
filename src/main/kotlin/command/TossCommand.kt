package command

import StickerBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import java.security.SecureRandom

class TossCommand : TextCommand("/toss", "Flip the coin") {

    private val random = SecureRandom.getInstance("SHA1PRNG").also { it.setSeed(System.currentTimeMillis()) }

    override fun execute(message: Message, botAPI: StickerBot): String? {
        val seed = random.nextInt(100000)

        val result = when {
            seed == 0 -> "A coin fell on the edge"
            seed == 99999 -> "A coin hung in the air"
            seed > 49999 -> "Tail"
            else -> "Head"
        }

        botAPI.execute(SendMessage(message.chatId, result).also { it.replyToMessageId = message.messageId })
        return null
    }

}

