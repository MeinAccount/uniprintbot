package remote

import com.google.cloud.Timestamp
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.PathElement
import com.google.cloud.datastore.Query
import com.google.cloud.datastore.StructuredQuery

object UserIliasNotificationStorage {
    fun getByUser(user: Entity) = datastore.run(Query.newEntityQueryBuilder()
            .setKind("UserIliasNotification")
            .setFilter(StructuredQuery.PropertyFilter.hasAncestor(user.key))
            .build()).asSequence()

    fun add(user: Entity, chatId: Long, messageId: Int, resource: IliasResource): Entity {
        val key = datastore.newKeyFactory()
                .addAncestor(PathElement.of("User", user.key.name))
                .setKind("UserIliasNotification").newKey()
        return datastore.add(Entity.newBuilder(key)
                .set("chatId", chatId)
                .set("messageId", messageId.toLong())
                .set("type", resource.type)
                .set("name", resource.name)
                .set("url", resource.url)
                .set("time", Timestamp.now())
                .build())
    }
}
