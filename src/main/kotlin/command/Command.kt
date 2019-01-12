package command

import org.telegram.telegrambots.meta.api.objects.Message

interface Command {
    fun process(message: Message)
    fun checkApplicable(name: String)
}