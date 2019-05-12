import java.io.File

interface MemeProvider {
    fun createMeme(imageFile: File, captionTokens: List<String>): File
}