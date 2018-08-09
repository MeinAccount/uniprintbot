package remote

import com.google.cloud.Timestamp
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.PathElement
import org.telegram.telegrambots.api.objects.Document

object UserStorage {
    private val userKeys = datastore.newKeyFactory().setKind("User")
    fun getUser(userId: Int): Entity? = datastore.get(userKeys.newKey(userId.toString()))


    fun saveUpload(user: Entity, document: Document) {
        val key = datastore.newKeyFactory()
                .addAncestor(PathElement.of("User", user.key.name))
                .setKind("Upload").newKey()

        datastore.put(Entity.newBuilder(key)
                .set("fileId", document.fileId)
                .set("fileName", document.fileName)
                .set("fileSize", document.fileSize.toLong())
                .set("timestamp", Timestamp.now())
                .build())
    }
}
