package remote

import com.google.cloud.Timestamp
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.PathElement
import com.google.cloud.datastore.Query
import com.google.cloud.datastore.StructuredQuery
import org.telegram.telegrambots.api.objects.Document

object UserStorage {
    fun getUser(userId: Int): Entity? = datastore.get(datastore.newKeyFactory()
            .setKind("User")
            .newKey(userId.toString()))

    fun listNotifyUsers() = datastore.run(Query.newEntityQueryBuilder()
            .setKind("User")
            .setFilter(StructuredQuery.PropertyFilter.eq("notify", true))
            .build()).iterator()


    fun logPrintJob(user: Entity, resource: IliasResource) {
        val key = datastore.newKeyFactory()
                .addAncestor(PathElement.of("User", user.key.name))
                .setKind("PrintJob").newKey()
        datastore.add(Entity.newBuilder(key)
                .set("kind", "iliasResource")
                .set("type", resource.type)
                .set("fileName", resource.name)
                .set("fileUrl", resource.url)
                .set("time", Timestamp.now())
                .build())
    }

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
