import java.awt.Image
import java.io.File

interface ImageProvider {
    fun getImageFile(image: File): File
}