package command

import ImageProvider
import StickerBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.stickers.CreateNewStickerSet
import org.telegram.telegrambots.meta.api.objects.File
import org.telegram.telegrambots.meta.api.objects.Message
import java.util.regex.Pattern

class CreateNewStickerSet(private val imageProvider: ImageProvider) : TextCommand("/create",
    "creates new sticker pack at the link https://t.me/addstickers/<stickerset_id>_by_<botname>, do not forget omit `< >` brackets\n" +
    "           - example: `/create my_new_stickerset_1 LUCKY CATS`") {

    private val verificationPattern = Pattern.compile("^[A-Za-z][\\w\\d_]*$")

    override fun execute(message: Message, botAPI: StickerBot): String? {
        val initialPhoto = if (message.hasPhoto()) {
            message.extractPhoto()!!
        } else {
            val replyToMessage = message.replyToMessage
            if (replyToMessage == null || !replyToMessage.hasPhoto()) return "Please provide at least one photo to create a sticker pack"
            replyToMessage.extractPhoto()!!
        }

        val tokens = message.tokenizeCommand().drop(1)

        if (tokens.size < 2) return "To create new sticker set name and title should be provided"

        val name = tokens[0]

        if (!verificationPattern.matcher(name).matches()) return "Name is incorrect $name"

        val title = tokens.drop(1).joinToString(" ")

        val stickerSetName = name.stickerPackName(botAPI.botUsername)

        val res = GetFile().setFileId(initialPhoto)
        val response2 = botAPI.execute(res) as File

        val file = botAPI.downloadFile(response2)

        val convertedImage = imageProvider.getImageFile(file)

        val userId = message.from!!.id

        val emodji = botAPI.getDefaultEmojy(userId.toLong()) ?: "☺️"

        botAPI.execute(CreateNewStickerSet(userId, stickerSetName, title, emodji).setPngStickerFile(convertedImage).also {
            it.containsMasks = false
        })

        botAPI.setCurrentStickerPack(userId, message.chatId, stickerSetName)
        botAPI.setUserState(userId, message.chatId, UserState.ADD)

        return "Your new sticker set is created and available by link ${stickerSetName.toStickerURL}"
    }
}
