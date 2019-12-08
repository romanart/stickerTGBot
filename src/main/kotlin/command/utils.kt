package command

import org.telegram.telegrambots.meta.api.objects.Message
import java.util.regex.Pattern

internal val emodjiPattern = Pattern.compile("[\\u20a0-\\u32ff\\ud83c\\udc00-\\ud83d\\udeff\\udbb9\\udce5-\\udbb9\\udcee]")

internal fun toEmodji(s: String?, d: String = "☺️") = s?.let { if (checkEmodji(it)) it else d } ?: d
internal fun checkEmodji(emodji: String) = emodjiPattern.matcher(emodji).matches()

internal val String.toStickerURL get() = "https://t.me/addstickers/${this}"
internal fun String.stickerPackName(botUsername: String)  = "${this}_by_$botUsername"

internal fun Message.extractPhoto() = photo?.last()?.fileId
internal fun Message.extractStickerPhoto() = sticker?.fileId

internal fun Message.tokenizeCommand() = (text ?: caption)?.split(" ") ?: emptyList()