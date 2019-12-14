package command

import StickerBot
import org.telegram.telegrambots.meta.api.objects.Message

abstract class BotCommand(val description: String) {
    abstract fun execute(message: Message, botAPI: StickerBot): String?
}

abstract class TextCommand(val name: String, val hidden: Boolean, description: String) : BotCommand(description) {

    constructor(name: String, description: String) : this(name, false, description)

    init {
        require(name[0] == '/')
    }

    protected fun tokenizeCommand(message: Message) = (message.text ?: message.caption)?.split(" ") ?: emptyList()

}

abstract class ActionCommand(val name: String, description: String) : BotCommand(description) {

    open fun checkAction(action: String) = action.startsWith(name) || action == name

    init {
        require(name[0] == '!')
    }
}

abstract class SpecialCommand(val state: UserState, description: String) : BotCommand(description) {
}
