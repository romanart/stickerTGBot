import kotlinx.serialization.json.JSON
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import kotlin.math.max
import kotlin.math.roundToInt


fun scale(imageSize: Pair<Int, Int>, scaleSize: Int) : Pair<Int, Int> {
    val ratio =  scaleSize.toFloat() / max(imageSize.first, imageSize.second)
    return Pair((imageSize.first * ratio).toInt(), (imageSize.second * ratio).toInt())
}

private const val maxSize = 512 * 1024

private lateinit var fontFile: File

fun resizeImage(image: Image, scale: Int): BufferedImage {
    val oldWidth = image.getWidth(null)
    val oldHeight = image.getHeight(null)
    val (newWidth, newHeight) = scale(Pair(oldWidth, oldHeight), scale)
    val newImage = image.getScaledInstance(newWidth, newHeight, Image.SCALE_DEFAULT)
    val bi = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
    val ig2 = bi.createGraphics()

    ig2.drawImage(newImage, 0, 0, null)
    ig2.dispose()
    return bi
}

object MockImageProvider: ImageProvider {
    override fun createMeme(imageFile: File, caption: String): File {
        val image = ImageIO.read(imageFile) ?: throw java.lang.Exception("Please don't feed me your bullshit!!!")
        val bufferedImage = BufferedImage(image.width, image.height, image.type)
        val g2d = bufferedImage.createGraphics()

        g2d.drawImage(image, 0, 0, null)

        val fontResource = javaClass.classLoader.getResource("Lobster-Regular.ttf").file
//        val fontResource = fontFile

        g2d.font = Font.createFont(Font.TRUETYPE_FONT, FileInputStream(fontResource)).run {
            val size = max(10F, (image.width * 0.1F))
            deriveFont(/*Font.BOLD, */size)
        }

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VRGB)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        val fm = g2d.fontMetrics

        if (fm.height > image.height) throw java.lang.Exception("Image is too small (height) to create a meme")

        val pixelsPerLine = bufferedImage.width - 10


        val transformedCaption = adoptToImage(caption, pixelsPerLine, fm)

        var topOffset = fm.height * transformedCaption.size

        for (s in transformedCaption) {
            val bounds = fm.getStringBounds(s, g2d)
            val x = (bufferedImage.width / 2 - bounds.width.toInt() / 2)
            val y = bufferedImage.height - topOffset - fm.ascent / 2 + bounds.height.toInt()

            g2d.paint = Color.BLACK
            g2d.drawString(s, x - 1, y - 1)
            g2d.drawString(s, x - 1, y + 1)
            g2d.drawString(s, x + 1, y - 1)
            g2d.drawString(s, x + 1, y + 1)
            g2d.paint = Color.WHITE
            g2d.drawString(s, x, y)

            topOffset -= bounds.height.roundToInt()
        }

        return File.createTempFile("save", ".jpg").also {
            it.deleteOnExit()
            drawImageIntoFile(bufferedImage, it)
            g2d.dispose()
        }
    }

    private fun drawImageIntoFile(bufferedImage: BufferedImage, outfile: File) {
        val writers = ImageIO.getImageWritersByFormatName("jpg")
        val writer = writers.next() as ImageWriter
        //        val outfile = File("tmp.jpg")
        val os = FileOutputStream(outfile)
        val ios = ImageIO.createImageOutputStream(os)
        writer.output = ios

        val param = writer.defaultWriteParam

        param.compressionMode = ImageWriteParam.MODE_DEFAULT

        writer.write(null, IIOImage(bufferedImage, null, null), param)
    }

    private fun adoptToImage(caption: String, charsInLine: Int, fm: FontMetrics): List<String> {

        if (charsInLine < fm.charWidth('W')) throw java.lang.Exception("Image is too small (width) to create a meme")

        val result = mutableListOf<String>()

        val words = caption.split(" ")

        var currentLineSize = 0
        val sb = StringBuilder()
        for (ww in words) {
            val w = "$ww "
            val wordSize = fm.stringWidth(w)

            if (wordSize > charsInLine) {
                result += sb.toString()
                sb.clear()
                result += w
                currentLineSize = 0
                continue
            } else if ((currentLineSize + wordSize) > charsInLine) {
                result += sb.toString()
                sb.clear()
                currentLineSize = 0
            }
            sb.append(w)
            currentLineSize += (wordSize)
        }

        if (sb.isNotBlank()) {
            result += sb.toString()
        }

        return result
    }

    override fun getImageFile(imageFile: File): File {
        val image = ImageIO.read(imageFile)
        val newImage = resizeImage(image, 512)
        val outfile = File.createTempFile("save", ".png").also { it.deleteOnExit() }
        ImageIO.write(newImage, "png", outfile)

        if (outfile.length() > maxSize) {
            compressImage(outfile)
        }

        return outfile
    }

    private fun compressImage(file: File) {
        var quality = 1.0f
        do {
            quality /= 2f
            compressImageImpl(file, quality)
        } while (file.length() > maxSize)
    }

    private fun compressImageImpl(file: File, quality: Float) {
        val image = ImageIO.read(file)
        val writers = ImageIO.getImageWritersByFormatName("jpg")
        val writer = writers.next() as ImageWriter
        val tmpFile = File.createTempFile("compress", ".jpg").also { it.deleteOnExit() }
        val os = FileOutputStream(tmpFile)
        val ios = ImageIO.createImageOutputStream(os)
        writer.output = ios

        val param = writer.defaultWriteParam

        param.compressionMode = ImageWriteParam.MODE_EXPLICIT
        param.compressionQuality = quality

        writer.write(null, IIOImage(image, null, null), param)

        os.close()
        ios.close()
        writer.dispose()

        val compressedImage = ImageIO.read(tmpFile)
        ImageIO.write(compressedImage, "png", file)
        tmpFile.delete()
    }
}


fun main(args: Array<String>) {
    println("I am Telegram sticker bot!")

    if (args.isEmpty()) throw Exception("Please provide config file")

//    val log4jProp = Properties()
//    log4jProp.setProperty("log4j.rootLogger", "TRACE")
//    PropertyConfigurator.configure(log4jProp)

    ApiContextInitializer.init()

    val botAPI = TelegramBotsApi()
//    fontFile = File(args[1])

    val config = JSON.parse(Config.serializer(), File(args[0]).readText())

    try {
        botAPI.registerBot(StickerBot(config, MockImageProvider))
    } catch (ex: TelegramApiException) {
        ex.printStackTrace()
    }
}