package remote

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.datastore.DatastoreOptions
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.*

internal val datastore = DatastoreOptions.newBuilder()
    .setCredentials(
        GoogleCredentials.fromStream(
            if (File("WEB-INF/uniprintbot.json").exists())
                FileInputStream("WEB-INF/uniprintbot.json")
            else FileInputStream("src/main/webapp/WEB-INF/uniprintbot.json")
        )
    )
    .build().service

data class IliasResource(
    val type: String, val name: String, val url: String,
    val hash: String, var telegram: TelegramResource
) {
    fun getPrintName() = "${type.replaceFirstChar { it.titlecase(Locale.getDefault()) }} ${
        name.replace("Aufgabe ", "A")
            .replace("Blatt ", "B").replace("Blatt", "B")
    }"

    fun processMessage(message: Message) {
        telegram = TelegramResource.UploadedTelegramResource(message.document.fileId)
    }
}

sealed class TelegramResource {
    abstract fun attach(name: String, command: SendDocument)

    data class UploadedTelegramResource(val fileId: String) : TelegramResource() {
        override fun attach(name: String, command: SendDocument) {
            command.document = InputFile(fileId)
        }
    }

    data class RemoteTelegramResource(val downloader: () -> InputStream) : TelegramResource() {
        override fun attach(name: String, command: SendDocument) {
            command.document = InputFile(downloader(), name)
        }
    }
}

data class ResourceOrigin(val name: String, val dbName: String, val retrieve: () -> List<Pair<String, String>>)
