import java.awt.Image
import java.io.File

interface ImageProvider {
    fun getImageFile(imageFile: File): File
    fun createMeme(imageFile: File, caption: String): File
}