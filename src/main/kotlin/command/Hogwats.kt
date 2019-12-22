package command

import StickerBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import java.security.SecureRandom
import kotlin.random.Random

enum class HogwartsHouse(val printName: String, val scoreColumn: String) {
    GRYFFINDOR("Gryffindor", "gryffindor_score"),
    RAVENCLAW("Ravenclaw", "ravenclaw_score"),
    HUFFLEPUDD("Hufflepuff", "hufflepuff_score"),
    SLYTHERING("Slytherin", "slythering_score")
}

private const val HOGWARTS_GAME_ROLE_TABLE = "hogwartsGameRole"
private const val HOGWARTS_STATS_TABLE = "hogwartsStats"

private fun checkGameIsStarted(chat_id: Long, botAPI: StickerBot): Boolean {
    val query = "SELECT chat_id FROM $HOGWARTS_STATS_TABLE WHERE chat_id = $chat_id;"

    return botAPI.executeQuery(query) { it.next() }
}

private fun getUserHouse(chat_id: Long, user_id: Int, botAPI: StickerBot): HogwartsHouse? {
    val query = "SELECT team_id FROM $HOGWARTS_GAME_ROLE_TABLE WHERE user_id = $user_id AND chat_id = $chat_id;"

    return botAPI.executeQuery(query) { r ->
        if (r.next()) HogwartsHouse.values()[r.getInt(1)] else null
    }
}

class StartHogwartsAction : ActionCommand("!играть", "Начинает игру в Хогвартс, надевайте шляпы и ловите снитчи") {

    private fun startGame(message: Message, botAPI: StickerBot) {
        val query =
            "INSERT INTO $HOGWARTS_STATS_TABLE " +
            "(chat_id, gryffindor_score, ravenclaw_score, hufflepuff_score, slythering_score, last_success) " +
            "VALUES (${message.chatId}, 0, 0, 0, 0, 'Никто еще не ловил снитч');"

        botAPI.executeUpdate(query)
    }

    override fun execute(message: Message, botAPI: StickerBot): String? {

        if (message.chat.isUserChat) return null

        if (checkGameIsStarted(message.chatId, botAPI)) return null

        startGame(message, botAPI)

        return "Игра в Хогвартс началась, надевайте шляпы и ловите снитчи!"
    }
}

class PutHatAction : ActionCommand("!надеть", "Надеть на первокурсника шляпу") {
    private val random =  SecureRandom.getInstance("SHA1PRNG").also { it.setSeed(System.currentTimeMillis()) }

    private fun rollHouse(): HogwartsHouse {
        val r = random.nextInt(1000)
        return when {
            r < 250 -> HogwartsHouse.GRYFFINDOR
            r < 500 -> HogwartsHouse.RAVENCLAW
            r < 750 -> HogwartsHouse.HUFFLEPUDD
            else -> HogwartsHouse.SLYTHERING
        }
    }

    private fun distributeToFaculty(chat_id: Long, user_id: Int, house: HogwartsHouse, botAPI: StickerBot) {
        val query =
            "INSERT INTO $HOGWARTS_GAME_ROLE_TABLE " +
            "(user_id, chat_id, team_id, snitch_score, prihod_score, last_stitch_time, last_prihod_time) " +
            "VALUES ($user_id, $chat_id, ${house.ordinal}, 0, 0, 1, 1);"


        botAPI.executeUpdate(query)
    }

    override fun execute(message: Message, botAPI: StickerBot): String? {
        if (message.chat.isUserChat) return null

        message.text?.let { if (it != "$name шляпу") return null } ?: return null

        if (!checkGameIsStarted(message.chatId, botAPI)) return "Сначала начните игру командой !играть"

        val house = getUserHouse(message.chatId, message.from.id, botAPI)

        if (house != null) {
            botAPI.execute(SendMessage(message.chatId, "Вас уже определили в ${house.printName}").also {
                it.replyToMessageId = message.messageId
            })
            return null
        }

        botAPI.execute(SendMessage(message.chatId, "Дайка мне подумать ...").also {
            it.replyToMessageId = message.messageId
        })

        val newHouse = rollHouse()

        distributeToFaculty(message.chatId, message.from.id, newHouse, botAPI)

        return  "Поздравляю, шляпа определила тебя на ${newHouse.printName}, добро пожаловать ... снова. И помни, если что-то не ловится, ты всегда можешь попробовать !договориться в деканате"
    }
}

abstract class CatchAction(private val subject: String, private val cooldown: Long) : ActionCommand("!поймать", "Пытаемся поймать $subject") {

    protected abstract val scoreColumn: String
    protected abstract val timeStampColumn: String
    protected abstract val teamScore: Boolean

    protected abstract val loseMessage: String

    protected abstract val winPonts: Int

    protected abstract fun winMessage(message: Message, house: HogwartsHouse, botAPI: StickerBot): String

    private val random = Random(System.nanoTime())

    private val millisecondComedown get() = cooldown * 60 * 1000

    private val fullActionName: String get() = "$name $subject"

    override fun checkAction(action: String): Boolean {
        return action == fullActionName
    }

    override fun execute(message: Message, botAPI: StickerBot): String? {

        if (message.isUserMessage) return null // Game is only for chat

        if (!checkGameIsStarted(message.chatId, botAPI)) return "Чтобы играть в Хогвартс начните игру командой !играть"

        val house = getUserHouse(message.chatId, message.from.id, botAPI) ?: return "Сначала наденьте шляпу, чтобы стать волшебником!"

        if (!checkCooldown(message, botAPI)) return "${message.userName()}, можете попробовать поймать $subject не чаще одного раза в $cooldown минут"

        updateCooldown(message, botAPI)

        if (random.nextInt(100) <= 90) return loseMessage

        val queryChat =
            "UPDATE $HOGWARTS_STATS_TABLE " +
             "SET last_success = '${message.userName().sanitaze()}', ${house.scoreColumn} = ${house.scoreColumn} + $winPonts "
             "WHERE chat_id = ${message.chatId};"

        botAPI.executeUpdate(queryChat)

        val queryUser =
            "UPDATE $HOGWARTS_GAME_ROLE_TABLE " +
            "SET $scoreColumn = $scoreColumn + 1 " +
            "WHERE chat_id = ${message.chatId} AND user_id = ${message.from.id};"

        botAPI.executeUpdate(queryUser)

        return winMessage(message, house, botAPI)
    }

    private fun checkCooldown(message: Message, botAPI: StickerBot): Boolean {
        val currentTime = System.currentTimeMillis()

        val query = "SELECT $timeStampColumn FROM $HOGWARTS_GAME_ROLE_TABLE WHERE chat_id = ${message.chatId} AND user_id = ${message.from.id};"

        val lastTimeStamp = botAPI.executeQuery(query) { r ->
            if (r.next()) r.getLong(1) else error("expecting last time stamp")
        }

        return (currentTime - lastTimeStamp >= millisecondComedown)
    }

    private fun updateCooldown(message: Message, botAPI: StickerBot) {
        val queryUser =
            "UPDATE $HOGWARTS_GAME_ROLE_TABLE " +
            "SET $timeStampColumn = ${System.currentTimeMillis()} " +
            "WHERE chat_id = ${message.chatId} AND user_id = ${message.from.id};"

        botAPI.executeUpdate(queryUser)
    }
}


class CatchASnithAction : CatchAction("снитч", 60) {
    override val scoreColumn = "snitch_score"
    override val timeStampColumn = "last_stitch_time"

    override val teamScore = true

    override val loseMessage = "Вы усердно всматриваетесь в небо, но снитч нигде не виден"

    override val winPonts = 150

    override fun winMessage(message: Message, house: HogwartsHouse, botAPI: StickerBot) =
        "${message.userName()}. Сегодня вы оказались самым ловким и везучим, снитч ваш, а с ним и победа для вашей комманды! ${house.printName} получает $winPonts очков!"
}

class CatchAPrihodAction : CatchAction("приход", 30) {
    override val scoreColumn ="prihod_score"
    override val timeStampColumn = "last_prihod_time"
    override val teamScore = false

    override val winPonts = 50

    override val loseMessage = "Вы усердно всматриваетесь в локтевой сгиб, но вены нигде не видно"

    override fun winMessage(message: Message, house: HogwartsHouse, botAPI: StickerBot) =
        "${message.userName()}. Сегодня вы оказались самым ловким и везучим наркопотребителем, приход ваш, а с ним и победа для вашей комманды! ${house.printName} получает утешительные $winPonts очков!!"

}

class HogwartsScoreAction : ActionCommand("!счет", "Счет по факультетам в поимке снитчей") {

    override fun execute(message: Message, botAPI: StickerBot): String? {

        if (message.chat.isUserChat) return null

        val query =
            "SELECT gryffindor_score, ravenclaw_score, hufflepuff_score, slythering_score, last_success " +
            "FROM $HOGWARTS_STATS_TABLE " +
            "WHERE chat_id = ${message.chatId};"

        return botAPI.executeQuery(query) { r ->
            if (r.next()) {
                val sb = StringBuilder("Счет по факультетам\n")

                sb.append("\t${HogwartsHouse.GRYFFINDOR.printName}\t- ${r.getString(1)}\n")
                sb.append("\t${HogwartsHouse.RAVENCLAW.printName}\t- ${r.getString(2)}\n")
                sb.append("\t${HogwartsHouse.HUFFLEPUDD.printName}\t- ${r.getString(3)}\n")
                sb.append("\t${HogwartsHouse.SLYTHERING.printName}\t- ${r.getString(4)}\n")
                sb.append("\tПоследним поймал снитч ${r.getString(5)}\n")

                sb.toString()

            } else "Сначала начните игру командой !играть"
        }

    }
}

class HogwartsPersonalScoreAction : ActionCommand("!мой", "Персональный счет по снитчам и приходам") {

    override fun execute(message: Message, botAPI: StickerBot): String? {

        if (message.chat.isUserChat) return null

        val query =
            "SELECT team_id, snitch_score, prihod_score " +
            "FROM $HOGWARTS_GAME_ROLE_TABLE " +
            "WHERE chat_id = ${message.chatId} AND user_id = ${message.from.id};"

        return botAPI.executeQuery(query) { r ->
            if (r.next()) {
                val sb = StringBuilder("Персональный счет для ${message.userName()}\n")

                sb.append("Факультет - ${HogwartsHouse.values()[r.getInt(1)].printName}\n")
                sb.append("Снитчи    - ${r.getInt(2)}\n")
                sb.append("Приходы   - ${r.getInt(3)}\n")

                sb.toString()
            } else "Сначала наденьте шляпу, чтобы стать волшебником!"
        }

    }
}

class NegotiateAction() : ActionCommand("!договориться", "Попробуем договориться с деканатом") {
    override fun execute(message: Message, botAPI: StickerBot): String? {
        if (message.chat.isUserChat) {
            return "Пока что не о чем договариваться, деканат не вышел из отпуска"
        }

        return null
    }
}

class NotifyAction(private val ownerId: Long) : ActionCommand("!notify", "Notify currently playing groups with provided message") {
    override fun execute(message: Message, botAPI: StickerBot): String? {
        if (message.chatId != ownerId) return null

        val notifyMessage = message.text.replace("!notify ", "")

        if (notifyMessage.isBlank()) return null

        val query = "SELECT chat_id FROM $HOGWARTS_STATS_TABLE;"

        val chats = botAPI.executeQuery(query) { r ->
            mutableListOf<Long>().apply {
                while (r.next()) {
                    add(r.getLong(1))
                }
            }
        }

        val msg = "Внамание, важное сообщение: $notifyMessage"

        for (chat_id in chats) {
            botAPI.execute(SendMessage(chat_id, msg))
        }

        return null
    }
}
