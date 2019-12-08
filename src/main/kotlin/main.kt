import database.AWSDatabaseConnection
import database.DatabaseAccessManager
import database.MySQLDatabaseLocalConnection
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.BasicCredentialsProvider
import org.glassfish.jersey.client.ClientProperties.PROXY_PASSWORD
import org.telegram.telegrambots.ApiContextInitializer
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.ApiContext
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Authenticator
import java.net.PasswordAuthentication
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

class MockMemeProvider(fontFile: File): MemeProvider {

    private val font = Font.createFont(Font.TRUETYPE_FONT, FileInputStream(fontFile))

    override fun createMeme(imageFile: File, captionTokens: List<String>): File {
        val image = ImageIO.read(imageFile) ?: throw java.lang.Exception("Please don't feed me your bullshit!!!")
        val bufferedImage = BufferedImage(image.width, image.height, image.type)
        val g2d = bufferedImage.createGraphics()

        g2d.drawImage(image, 0, 0, null)

        g2d.font = font.run {
            val size = max(10F, (image.width * 0.1F))
            deriveFont(/*Font.BOLD, */size)
        }

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VRGB)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        val fm = g2d.fontMetrics

        if (fm.height > image.height) throw java.lang.Exception("Image is too small (height) to create a meme")

        val pixelsPerLine = bufferedImage.width - 10


        val transformedCaption = adoptToImage(captionTokens, pixelsPerLine, fm)

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

    private fun adoptToImage(captionTokens: List<String>, charsInLine: Int, fm: FontMetrics): List<String> {

        if (charsInLine < fm.charWidth('W')) throw java.lang.Exception("Image is too small (width) to create a meme")

        val result = mutableListOf<String>()

        val words = captionTokens

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

}

class MockImageProvider(private val workingDirectory: File): ImageProvider {

    override fun getImageFile(imageFile: File): File {
        val image = ImageIO.read(imageFile)
        val newImage = resizeImage(image, 512)
        val outfile = createTempFile("save", ".png", workingDirectory)
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
        val tmpFile = createTempFile("compress", ".jpg", workingDirectory)
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

    val cli = CliInterface(args)

    ApiContextInitializer.init()

    val botAPI = TelegramBotsApi()
    val workingDirectory = createTempDir().also { it.mkdir() }
    val config = parseConfig(cli.configFile)
    val botOptions = ApiContext.getInstance(DefaultBotOptions::class.java)

    val dbConnection = DatabaseAccessManager(config.databaseConfig).createDatabaseConnection()

    println(dbConnection)

    try {
        botAPI.registerBot(StickerBot(config, MockImageProvider(workingDirectory), MockMemeProvider(cli.fontFile), dbConnection, workingDirectory, botOptions))
    } catch (ex: TelegramApiException) {
        ex.printStackTrace()
    }
}