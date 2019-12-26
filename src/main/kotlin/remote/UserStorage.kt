package remote

import com.google.cloud.Timestamp
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.PathElement
import com.google.cloud.datastore.Query
import com.google.cloud.datastore.StructuredQuery
import org.telegram.telegrambots.meta.api.objects.Document
import org.telegram.telegrambots.meta.api.objects.Message

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

    fun setName(user: Entity, name: String): Entity {
        val newUser = Entity.newBuilder(user)
                .set("name", name).build()

        datastore.update(newUser)
        return newUser
    }


    fun listUsers() = datastore.run(Query.newEntityQueryBuilder()
            .setKind("User")
            .build()).iterator()

    fun logPrintJob(user: Entity, document: Document, printedMessage: Message?, commandMessage: Message?) {
        val key = datastore.newKeyFactory()
                .addAncestor(PathElement.of("User", user.key.name))
                .setKind("PrintJob").newKey()
        val builder = Entity.newBuilder(key)
                .set("kind", "telegramFile")
                .set("fileId", document.fileId)
                .set("fileName", document.fileName)
                .set("fileSize", document.fileSize.toLong())
                .set("time", Timestamp.now())

        if (printedMessage != null) builder.set("printedMessageID", printedMessage.messageId.toLong())
        if (commandMessage != null) builder.set("commandMessageID", commandMessage.messageId.toLong())
        datastore.add(builder.build())
    }


    fun listPrintJobsOneDay(): Iterator<Entity> {
        val now = System.currentTimeMillis()
        val oneDayAgo = Timestamp.of(java.sql.Timestamp(now - 1 * 24 * 60 * 60 * 1000))
        val twoDaysAgo = Timestamp.of(java.sql.Timestamp(now - 2 * 24 * 60 * 60 * 1000))

        return datastore.run(Query.newEntityQueryBuilder()
                .setKind("PrintJob")
                .setFilter(StructuredQuery.CompositeFilter.and(
                        StructuredQuery.PropertyFilter.gt("time", twoDaysAgo),
                        StructuredQuery.PropertyFilter.le("time", oneDayAgo)
                )).build()).iterator()
    }
}
