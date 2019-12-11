package command

import ImageProvider
import StickerBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.stickers.AddStickerToSet
import org.telegram.telegrambots.meta.api.objects.File
import org.telegram.telegrambots.meta.api.objects.Message

private class AddExecutor(private val imageProvider: ImageProvider) {


    private fun addStickerToStickerSet(message: Message, setName: String, owner_id: Int, botAPI: StickerBot): String {
        val sticker = message.sticker!!
        val fileID = sticker.fileId
        val fileResponse = botAPI.execute(GetFile().setFileId(fileID)) as File
        val file = botAPI.downloadFile(fileResponse)
        botAPI.execute(AddStickerToSet(owner_id, setName, sticker.emoji).setPngSticker(file))
        return "Added new sticker to ${setName.toStickerURL}"
    }

    private fun addPhotoToStickerSet(
        message: Message,
        setName: String,
        emoji: String,
        owner_id: Int,
        botAPI: StickerBot
    ): String {
        val fileID = message.extractPhoto()
        val filePhoto = botAPI.execute(GetFile().setFileId(fileID)) as File

        val file = botAPI.downloadFile(filePhoto)

        val convertedImage = imageProvider.getImageFile(file)

        botAPI.execute(AddStickerToSet(owner_id, setName, emoji).setPngSticker(convertedImage))
        return "Add new sticker to ${setName.toStickerURL}"
    }

    private fun extractEmoji(message: Message) = message.tokenizeCommand().firstOrNull { checkEmodji(it) }

    fun execute(message: Message, stickerSetName: String, owner_id: Int, botAPI: StickerBot): String? {
        try {
            val currentStickerSet = stickerSetName
//
            val emoji = extractEmoji(message) ?: botAPI.getDefaultEmojy(message.chatId) ?: "☺️"
            if (message.hasPhoto()) return addPhotoToStickerSet(message, currentStickerSet, emoji, owner_id, botAPI)
            if (message.hasSticker()) return addStickerToStickerSet(message, currentStickerSet, owner_id, botAPI)
            val replyMessage = message.replyToMessage ?: return "Nothing to add"
            if (replyMessage.hasPhoto()) return addPhotoToStickerSet(
                replyMessage,
                currentStickerSet,
                emoji,
                owner_id,
                botAPI
            )
            if (replyMessage.hasSticker()) return addStickerToStickerSet(replyMessage, currentStickerSet, owner_id, botAPI)
            return "Nothing to add"
        } catch (e: Throwable) {
            if (message.chat.isUserChat) throw e
            return null
        }
    }
}

class AddCommand(imageProvider: ImageProvider): TextCommand("/add", "Add image or replied sticker to current sticker pack") {

    private val executor = AddExecutor(imageProvider)


    override fun execute(message: Message, botAPI: StickerBot): String? {
        val stickerSetName = botAPI.getCurrentStickerSet(message.from.id, message.chatId) ?: return "Please chose sticker pack first"
        return executor.execute(message, stickerSetName, message.from.id, botAPI)
    }
}

class AddSpecialCommand(imageProvider: ImageProvider) : SpecialCommand(UserState.ADD,  "Add image or replied sticker to current sticker pack") {
    private val executor = AddExecutor(imageProvider)

    override fun execute(message: Message, botAPI: StickerBot): String? {
        val stickerSetName = botAPI.getCurrentStickerSet(message.from.id, message.chatId) ?: return "Please chose sticker pack first"
        return executor.execute(message, stickerSetName, message.from.id, botAPI)
    }
}

class AddGroupCommand(imageProvider: ImageProvider) : TextCommand("/gadd", "Add sticker to group sticker pack") {
    private val executor = AddExecutor(imageProvider)

    override fun execute(message: Message, botAPI: StickerBot): String? {
        val groupStickerPack = botAPI.getGroupStickerPack(message.chatId) ?: return "Select sticker pack using /gselect"

        val ownerId = botAPI.getStickerPackOwner(groupStickerPack)

        require(ownerId >= 0)

        return executor.execute(message, groupStickerPack, ownerId, botAPI)
    }

}
