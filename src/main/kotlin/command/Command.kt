package command

import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import SessionState
import mu.KLogger

abstract class Command<in S: SessionState>(val name: String, protected val logger: KLogger, protected val botAPI: TelegramLongPollingBot) {
    abstract val isChatCommand: Boolean
    abstract val isGroupCommand: Boolean

    protected val botName = botAPI.botUsername

    abstract fun verifyArguments(tokens: List<String>): Boolean
    protected open fun verifyContent(message: Message) = true
    protected open fun verifyCommand(command: String) = command == "/$name" || command == "/$name@$botName"
    protected abstract fun verifyState(state: SessionState): Boolean
    protected open fun verifyPermissions(message: Message) = true

    protected open fun onArgumentsFail(message: Message) {}
    protected open fun onStateFail(message: Message, state: SessionState) {}
    protected open fun onContentFail(message: Message) {}
    protected open fun onPermissionFail(message: Message) {}

    protected fun tokenizeCommand(message: Message) = (message.text ?: message.caption)?.split(" ") ?: emptyList()

    fun checkCommand(message: Message, state: SessionState): Boolean {
        val tokens = tokenizeCommand(message)
        val command = tokens.firstOrNull() ?: ""

        return if (verifyCommand(command)) {
            if (!verifyArguments(tokens)) {
                onArgumentsFail(message)
                return false
            }
            if (!verifyContent(message)) {
                onContentFail(message)
                return false
            }
            if (!verifyState(state)) {
                onStateFail(message, state)
                return false
            }
            return true
        } else false
    }

    protected fun extractPhoto(message: Message) = message.photo.last().fileId
    abstract fun process(message: Message, state: S) : SessionState
}

