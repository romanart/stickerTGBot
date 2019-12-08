package command

import StickerBot
import org.apache.commons.io.input.ReversedLinesFileReader
import org.apache.log4j.FileAppender
import org.apache.log4j.Logger
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import java.nio.charset.Charset
import kotlin.math.min

class LogCommand(private val ownerID: Long): TextCommand("/log", true, "Print bot log to admin chat") {
    private val defaultLogLineCount = 30
    private val messageSizeLimit = 4096

    override fun execute(message: Message, botAPI: StickerBot): String? {
        if (!message.isUserMessage) return null
        if (message.chatId != ownerID) return null

        val tokens = tokenizeCommand(message)
        var limit = min(
            if (tokens.size > 1) {
                tokens[1].toIntOrNull() ?: defaultLogLineCount
            } else defaultLogLineCount, 1024
        )

        val appenders = Logger.getRootLogger().allAppenders
        val fileAppender = appenders.nextElement() as FileAppender
        val loggingFile = java.io.File(fileAppender.file)
        val fileReader = ReversedLinesFileReader(
            loggingFile,
            fileAppender.encoding?.let { Charset.forName(it) } ?: Charset.defaultCharset())
        val lines = arrayOfNulls<String>(limit)
        for (i in 0 until limit) {
            lines[limit - i - 1] = fileReader.readLine()
        }

        val sb = StringBuilder()
        while (limit > 0) {
            val newLine = lines[lines.size - limit--]!!
            val appendSize = newLine.length + 1 // '\n' symbol
            if (sb.length + appendSize >= messageSizeLimit) {
                botAPI.execute(SendMessage(message.chatId, sb.toString()))
                sb.clear()
            }
            sb.append(newLine)
            sb.append('\n')
        }
        if (sb.isNotEmpty()) {
            botAPI.execute(SendMessage(message.chatId, sb.toString()))
        }
        fileReader.close()

        return null
    }
}
