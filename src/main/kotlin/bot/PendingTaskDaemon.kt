package bot

import java.util.concurrent.BlockingQueue

class PendingTaskDaemon(private val queue: BlockingQueue<BotTask>, private val botApi: StickerBot) : Runnable {
    private fun isValidToExecute(task: BotTask?): Boolean {
        val current = System.currentTimeMillis()
        return task != null && current >= task.time
    }

    override fun run() {
        while (true) {
            if (isValidToExecute(queue.peek())) {
                val task = queue.take()
                try {
                    task.execute(botApi)
                } catch (e: Throwable) {
                    botApi.botLogger.warn { e.toString() }
                    botApi.botLogger.warn { e.stackTrace.joinToString(separator = "\n") }
                }
            } else {
                Thread.sleep(1.minutesToMillis)
            }
        }
    }
}