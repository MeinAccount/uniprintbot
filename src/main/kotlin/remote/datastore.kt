package remote

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.Timestamp
import com.google.cloud.datastore.DatastoreOptions
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.PathElement
import org.telegram.telegrambots.api.objects.Document
import java.io.File
import java.io.FileInputStream

private val datastore = DatastoreOptions.newBuilder()
        .setCredentials(GoogleCredentials.fromStream(
                if (File("WEB-INF/uniprintbot.json").exists())
                    FileInputStream("WEB-INF/uniprintbot.json")
                else FileInputStream("src/main/webapp/WEB-INF/uniprintbot.json")))
        .build().service

private val userKeys = datastore.newKeyFactory().setKind("User")
fun getUser(userId: Int): Entity? = datastore.get(userKeys.newKey(userId.toString()))

fun saveUpload(user: Entity, document: Document) {
    val key = datastore.newKeyFactory()
            .addAncestor(PathElement.of("User", user.key.name))
            .setKind("Upload")
            .newKey()

    datastore.put(Entity.newBuilder(key)
            .set("fileId", document.fileId)
            .set("fileName", document.fileName)
            .set("fileSize", document.fileSize.toLong())
            .set("timestamp", Timestamp.now())
            .build())
}
