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

    fun toggleUserNotify(user: Entity, field: String): Entity {
        val newUser = Entity.newBuilder(user)
                .set(field, if (user.contains(field)) !user.getBoolean(field) else true).build()

        datastore.update(newUser)
        return newUser
    }


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
