package remote

import com.google.cloud.Timestamp
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.PathElement
import com.google.cloud.datastore.Query
import com.google.cloud.datastore.StructuredQuery
import org.telegram.telegrambots.meta.api.objects.Message

object UserIliasNotificationStorage {
    fun getByUser(user: Entity) = datastore.run(Query.newEntityQueryBuilder()
            .setKind("UserIliasNotification")
            .setFilter(StructuredQuery.PropertyFilter.hasAncestor(user.key))
            .build()).asSequence()

    fun add(user: Entity, message: Message, resource: IliasResource): Entity {
        val key = datastore.newKeyFactory()
                .addAncestor(PathElement.of("User", user.key.name))
                .setKind("UserIliasNotification").newKey()
        return datastore.add(Entity.newBuilder(key)
                .set("chatId", message.chatId)
                .set("messageId", message.messageId.toLong())
                .set("type", resource.type)
                .set("name", resource.name)
                .set("url", resource.url)
                .set("hash", resource.hash)
                .set("time", Timestamp.now())
                .build())
    }

    fun update(notification: Entity, message: Message, resource: IliasResource) {
        datastore.update(Entity.newBuilder(notification)
                .set("messageId", message.messageId.toLong())
                .set("name", resource.name)
                .set("hash", resource.hash)
                .set("time", Timestamp.now())
                .build())
    }
}
