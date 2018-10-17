package remote

import com.google.cloud.Timestamp
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.PathElement
import com.google.cloud.datastore.Query
import org.telegram.telegrambots.meta.api.objects.Document

object UserStorage {
    fun getUser(userId: Int): Entity? = datastore.get(datastore.newKeyFactory()
            .setKind("User")
            .newKey(userId.toString()))

    fun listUsers() = datastore.run(Query.newEntityQueryBuilder()
            .setKind("User")
            .build()).iterator()

    fun logPrintJob(user: Entity, document: Document) {
        val key = datastore.newKeyFactory()
                .addAncestor(PathElement.of("User", user.key.name))
                .setKind("PrintJob").newKey()
        datastore.add(Entity.newBuilder(key)
                .set("kind", "telegramFile")
                .set("fileId", document.fileId)
                .set("fileName", document.fileName)
                .set("fileSize", document.fileSize.toLong())
                .set("time", Timestamp.now())
                .build())
    }
}
