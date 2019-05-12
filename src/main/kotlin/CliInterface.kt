
import kotlinx.cli.*
import java.io.File

class CliInterface(args: Array<String>): CommandLineInterface("StickerBot") {
    private val configFileName by flagValueArgument("-config", "configFile", "JSON configuration")
    private val fontFileName by flagValueArgument("-font", "FontTTF", "Font ")

    init {
        parse(args)
    }

    val  configFile get() = File(configFileName)
    val  fontFile get() = File(fontFileName)

}