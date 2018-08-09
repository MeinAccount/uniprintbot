package remote

import com.google.cloud.Timestamp
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.PathElement
import com.google.cloud.datastore.Query
import com.google.cloud.datastore.StructuredQuery
import org.telegram.telegrambots.api.objects.Document

object UserStorage {
    private val userKeys = datastore.newKeyFactory().setKind("User")
    fun getUser(userId: Int): Entity? = datastore.get(userKeys.newKey(userId.toString()))

    fun listNotifyUsers() = datastore.run(Query.newEntityQueryBuilder()
            .setKind("User")
            .setFilter(StructuredQuery.PropertyFilter.eq("notify", true))
            .build()).iterator()


    fun saveUpload(user: Entity, document: Document) {
        val key = datastore.newKeyFactory()
                .addAncestor(PathElement.of("User", user.key.name))
                .setKind("Upload").newKey()

        datastore.add(Entity.newBuilder(key)
                .set("fileId", document.fileId)
                .set("fileName", document.fileName)
                .set("fileSize", document.fileSize.toLong())
                .set("timestamp", Timestamp.now())
                .build())
    }
}
