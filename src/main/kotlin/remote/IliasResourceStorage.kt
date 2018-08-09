package remote

import com.google.cloud.Timestamp
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.PathElement
import com.google.cloud.datastore.Query
import com.google.cloud.datastore.StructuredQuery

object IliasResourceStorage {
    fun get(user: Entity, chatId: Long, messageId: Int): List<IliasResource> {
        val query = Query.newEntityQueryBuilder()
                .setKind("IliasResource")
                .setFilter(StructuredQuery.CompositeFilter.and(
                        StructuredQuery.PropertyFilter.hasAncestor(user.key),
                        StructuredQuery.PropertyFilter.eq("chatId", chatId),
                        StructuredQuery.PropertyFilter.eq("messageId", messageId.toLong())
                )).build()
        val results = datastore.run(query)

        return results.asSequence().map { entity ->
            IliasResource(entity.getString("type"), entity.getString("name"), entity.getString("url"),
                    entity.getBoolean("selected"), entity)
        }.sortedBy { it.name }.toList()
    }


    /**
     * Saves the [IliasResource]s. Call [updateMessage] to set the assosiated chat and message ids.
     */
    fun save(user: Entity, iliasResources: List<IliasResource>) {
        datastore.runInTransaction { transaction ->
            iliasResources.forEach { resource ->
                val key = datastore.newKeyFactory()
                        .addAncestor(PathElement.of("User", user.key.name))
                        .setKind("IliasResource").newKey()
                resource.entity = transaction.put(Entity.newBuilder(key)
                        .set("type", resource.type)
                        .set("name", resource.name)
                        .set("url", resource.url)
                        .set("selected", resource.selected)
                        .set("lastUsed", Timestamp.now())
                        .build())
            }
        }
    }

    fun updateMessage(chatId: Long, messageId: Int, iliasResources: List<IliasResource>) {
        datastore.runInTransaction { transaction ->
            iliasResources.forEach { resource ->
                transaction.update(Entity.newBuilder(resource.entity)
                        .set("chatId", chatId)
                        .set("messageId", messageId.toLong())
                        .build())
            }
        }
    }


    fun updateSelected(resource: IliasResource) {
        datastore.update(Entity.newBuilder(resource.entity)
                .set("selected", resource.selected)
                .set("lastUsed", Timestamp.now())
                .build())
    }

    fun delete(iliasResources: List<IliasResource>) {
        datastore.runInTransaction { transaction ->
            iliasResources.forEach { resource ->
                transaction.delete(resource.entity?.key)
            }
        }
    }
}
