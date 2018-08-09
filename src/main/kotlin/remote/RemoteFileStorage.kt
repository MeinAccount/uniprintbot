package remote

import com.google.cloud.Timestamp
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.PathElement
import com.google.cloud.datastore.Query
import com.google.cloud.datastore.StructuredQuery

object RemoteFileStorage {
    fun get(user: Entity, chatId: Long, messageId: Int): List<RemoteFile> {
        val query = Query.newEntityQueryBuilder()
                .setKind("RemoteFile")
                .setFilter(StructuredQuery.CompositeFilter.and(
                        StructuredQuery.PropertyFilter.hasAncestor(user.key),
                        StructuredQuery.PropertyFilter.eq("chatId", chatId),
                        StructuredQuery.PropertyFilter.eq("messageId", messageId.toLong())
                )).build()
        val results = datastore.run(query)

        return results.asSequence().map { entity ->
            RemoteFile(entity.getString("type"), entity.getString("name"), entity.getString("url"),
                    entity.getBoolean("selected"), entity)
        }.sortedBy { it.name }.toList()
    }


    /**
     * Saves the [RemoteFile]s. Call [updateMessage] to set the assosiated chat and message ids.
     */
    fun save(user: Entity, remoteFiles: List<RemoteFile>) {
        datastore.runInTransaction { transaction ->
            remoteFiles.forEach { file ->
                val key = datastore.newKeyFactory()
                        .addAncestor(PathElement.of("User", user.key.name))
                        .setKind("RemoteFile").newKey()
                file.entity = transaction.put(Entity.newBuilder(key)
                        .set("type", file.type)
                        .set("name", file.name)
                        .set("url", file.url)
                        .set("selected", file.selected)
                        .set("lastUsed", Timestamp.now())
                        .build())
            }
        }
    }

    fun updateMessage(chatId: Long, messageId: Int, remoteFiles: List<RemoteFile>) {
        datastore.runInTransaction { transaction ->
            remoteFiles.forEach { file ->
                transaction.update(Entity.newBuilder(file.entity)
                        .set("chatId", chatId)
                        .set("messageId", messageId.toLong())
                        .build())
            }
        }
    }


    fun updateSelected(file: RemoteFile) {
        datastore.update(Entity.newBuilder(file.entity)
                .set("selected", file.selected)
                .set("lastUsed", Timestamp.now())
                .build())
    }

    fun delete(remoteFiles: List<RemoteFile>) {
        datastore.runInTransaction { transaction ->
            remoteFiles.forEach { file ->
                transaction.delete(file.entity?.key)
            }
        }
    }
}
