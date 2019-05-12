import java.io.File

interface ImageProvider {
    fun getImageFile(imageFile: File): File
}