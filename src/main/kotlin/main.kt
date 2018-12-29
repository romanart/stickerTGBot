import kotlinx.serialization.json.JSON
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import kotlin.math.max


fun scale(imageSize: Pair<Int, Int>, scaleSize: Int) : Pair<Int, Int> {
    val ratio =  scaleSize.toFloat() / max(imageSize.first, imageSize.second)
    return Pair((imageSize.first * ratio).toInt(), (imageSize.second * ratio).toInt())
}

private const val maxSize = 512 * 1024

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
    override fun getImageFile(image: File): File {
        val image = ImageIO.read(image)
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

    val config = JSON.parse(Config.serializer(), File(args[0]).readText())

    try {
        botAPI.registerBot(RomanTestFirstBot(config, MockImageProvider))
    } catch (ex: TelegramApiException) {
        ex.printStackTrace()
    }
}