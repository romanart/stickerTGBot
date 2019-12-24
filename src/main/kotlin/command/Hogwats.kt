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
private const val HOGWARTS_NICK_NAMES_TABLE = "hogwartsPlayerNickname"
private const val HOGWARTS_CHEAT_TABLE = "hogwartsCheat"

private var dailyPuzzleQuestion = "Кто сегодня лох?"
private var dailyPuzzleAnswer = "ты"

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

private fun updateNickName(user_id: Int, nickname: String, botAPI: StickerBot) {
    val newName = nickname.sanitaze()
    val query =
        "INSERT INTO $HOGWARTS_NICK_NAMES_TABLE (user_id, name) " +
        "VALUES ($user_id, '$newName') ON DUPLICATE KEY " +
        "UPDATE name = '$newName';"

    botAPI.executeUpdate(query)
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

        botAPI.execute(SendMessage(message.chatId, "Дай-ка мне подумать ...").also {
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

    private val Int.timeFraction get() = (this / 4) * 60 * 1000
    private val Int.chanceFraction get() = this / 3

    private fun selectCheat(user_id: Int, botAPI: StickerBot): Int {
        val timeStamp = System.currentTimeMillis()

        return botAPI.executeQuery("SELECT magic_value, time_stamp FROM $HOGWARTS_CHEAT_TABLE WHERE user_id = $user_id;") { r ->
            if (r.next()) {
                val value = r.getInt(1)
                val endTime = r.getLong(2)
                if (timeStamp < endTime) value else 0
            } else 0
        }
    }

    override fun execute(message: Message, botAPI: StickerBot): String? {

        if (message.isUserMessage) return null // Game is only for chat

        if (!checkGameIsStarted(message.chatId, botAPI)) return "Чтобы играть в Хогвартс начните игру командой !играть"

        val house = getUserHouse(message.chatId, message.from.id, botAPI) ?: return "Сначала наденьте шляпу, чтобы стать волшебником!"

        val userName = message.userName()

        updateNickName(message.from.id, userName, botAPI)

        val cheatValue = selectCheat(message.from.id, botAPI)

        if (!checkCooldown(message, botAPI, cheatValue)) return "$userName, можете попробовать поймать $subject не чаще одного раза в примерно $cooldown минут"

        updateCooldown(message, botAPI)

        if ((random.nextInt(100) + cheatValue.chanceFraction) <= 90) return loseMessage

        val queryChat =
            "UPDATE $HOGWARTS_STATS_TABLE " +
             "SET last_success = '${userName.sanitaze()}', ${house.scoreColumn} = ${house.scoreColumn} + $winPonts "
             "WHERE chat_id = ${message.chatId};"

        botAPI.executeUpdate(queryChat)

        val queryUser =
            "UPDATE $HOGWARTS_GAME_ROLE_TABLE " +
            "SET $scoreColumn = $scoreColumn + 1 " +
            "WHERE chat_id = ${message.chatId} AND user_id = ${message.from.id};"

        botAPI.executeUpdate(queryUser)

        return winMessage(message, house, botAPI)
    }

    private fun checkCooldown(message: Message, botAPI: StickerBot, cheatValue: Int): Boolean {
        val currentTime = System.currentTimeMillis()

        val query = "SELECT $timeStampColumn FROM $HOGWARTS_GAME_ROLE_TABLE WHERE chat_id = ${message.chatId} AND user_id = ${message.from.id};"

        val lastTimeStamp = botAPI.executeQuery(query) { r ->
            if (r.next()) r.getLong(1) else error("expecting last time stamp")
        }

        return ((currentTime - lastTimeStamp) >= (millisecondComedown - cheatValue.timeFraction))
    }

    private fun updateCooldown(message: Message, botAPI: StickerBot) {
        val seed = cooldown.toInt() / 4
        val deltaMin = random.nextInt(2 * seed) - seed
        val cooldown = System.currentTimeMillis() + deltaMin * 60 * 1000

        val queryUser =
            "UPDATE $HOGWARTS_GAME_ROLE_TABLE " +
            "SET $timeStampColumn = $cooldown " +
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

class HogwartsPlayerList : ActionCommand("!список", "Список игроков") {
    private fun selectNickNames(chat_id: Long, botAPI: StickerBot): List<List<String>> {
        val queryUserIds =
            "SELECT user_id, team_id FROM $HOGWARTS_GAME_ROLE_TABLE WHERE chat_id = $chat_id;"

        val teamMap = mutableMapOf<Int, Int>()

        botAPI.executeQuery(queryUserIds) { r ->
            while (r.next()) {
                val user = r.getInt(1)
                val team = r.getInt(2)
                teamMap[user] = team
            }
        }

        if (teamMap.isEmpty()) return emptyList()

        val queryBuilder = StringBuilder("SELECT user_id, name FROM $HOGWARTS_NICK_NAMES_TABLE WHERE ")

        val predicateBuilder = teamMap.keys.joinTo(queryBuilder, " OR ") { "user_id = $it" }

        predicateBuilder.append(';')

        val teamNickNames = listOf(mutableListOf<String>(), mutableListOf<String>(), mutableListOf<String>(), mutableListOf<String>())

        botAPI.executeQuery(predicateBuilder.toString()) { r ->
            while (r.next()) {
                val user = r.getInt(1)
                val nickName = r.getString(2)
                val team = teamMap[user] ?: error("No team for user '$nickName' found")
                teamNickNames[team].add(nickName)
            }
        }

        return teamNickNames
    }

    private fun StringBuilder.printHousePlayers(house: HogwartsHouse, nicknames: List<List<String>>) {
        append('\t')
        append(house.printName)
        append(" - ")
        nicknames[house.ordinal].joinTo(this, ", ", postfix = "\n")
    }

    override fun execute(message: Message, botAPI: StickerBot): String? {
        if (message.chat.isUserChat) return null

        if (!checkGameIsStarted(message.chatId, botAPI)) return "Сначала начните игру командой !играть"

        val nicknames = selectNickNames(message.chatId, botAPI)

        val sb = StringBuilder("Список игроков\n")

        HogwartsHouse.values().forEach { sb.printHousePlayers(it, nicknames) }

        return sb.toString()
    }
}

class HogwartsPersonalScoreAction : ActionCommand("!мой", "Персональный счет по снитчам и приходам") {

    override fun execute(message: Message, botAPI: StickerBot): String? {

        if (message.chat.isUserChat) return null

        updateNickName(message.from.id, message.userName(), botAPI)

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

class NegotiateAction : ActionCommand("!договориться", "Попробуем договориться с деканатом") {
    override fun execute(message: Message, botAPI: StickerBot): String? {
        if (!message.chat.isUserChat) return null

        return "Отправь мне !ответ на вопрос: $dailyPuzzleQuestion"
    }
}

class AnswerAction : ActionCommand("!ответ", "Попробуем договориться с деканатом") {

    companion object {
        private const val CHEAT_VALUE = 100
        private const val CHEAT_DURATION = 2 * 60 * 60 * 1000
    }

    override fun execute(message: Message, botAPI: StickerBot): String? {
        if (!message.chat.isUserChat) return null

        val checkQuery = "SELECT magic_value FROM $HOGWARTS_CHEAT_TABLE WHERE user_id = ${message.from.id};"

        val existedCheat = botAPI.executeQuery(checkQuery) { r ->
            if (r.next()) r.getInt(1) else 0
        }

        if (existedCheat != 0) return "Ты уже сегодня читерил, так часто нельзя"

        val answer = dailyPuzzleAnswer

        val userAnswer = message.text.replace("!ответ ", "").trim()

        if (userAnswer != answer) return "Ответ неверный, попробуй еще раз, если забыл вопрос - спроси с помощью !договориться"

        val endTimeStamp = System.currentTimeMillis() + CHEAT_DURATION

        val query = "INSERT INTO $HOGWARTS_CHEAT_TABLE (user_id, magic_value, time_stamp) VALUES (${message.from.id}, $CHEAT_VALUE, $endTimeStamp);"

        botAPI.executeUpdate(query)

        return "Поздравляю ${message.userName()}, ты выйграл читерство на некоторое время, пототропись фармить снитчи!"
    }
}

abstract class SetPuzzleValue(private val ownerId: Long, actionName: String, private val valueName: String) : ActionCommand("!$actionName", "Set current puzzle $valueName") {

    abstract fun updateValue(value: String): String

    override fun execute(message: Message, botAPI: StickerBot): String? {
        if (message.chatId != ownerId) return null

        val value = message.text.replace("$name ", "").trim()

        val newValue = updateValue(value)

        return "$valueName is set to '$newValue'"
    }
}

class SetPuzzleQuestion(ownerId: Long) : SetPuzzleValue(ownerId, "setQuestion", "question") {
    override fun updateValue(value: String): String {
        dailyPuzzleQuestion = value
        return value
    }
}
class SetPuzzleAnswer(ownerId: Long) : SetPuzzleValue(ownerId, "setAnswer", "answer") {
    override fun updateValue(value: String): String {
        dailyPuzzleAnswer = value
        return value
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
