package heiko

import com.google.cloud.Timestamp
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.Query
import com.google.cloud.datastore.QueryResults
import com.google.cloud.datastore.StructuredQuery
import org.telegram.telegrambots.meta.api.objects.Message
import remote.datastore
import java.time.LocalDate

fun storeSDate(date: LocalDate, message: Message): Entity {
    return datastore.add(Entity.newBuilder(datastore.newKeyFactory().setKind("SDate").newKey())
            .set("year", date.year.toLong())
            .set("month", date.monthValue.toLong())
            .set("day", date.dayOfMonth.toLong())

            .set("chatId", message.chatId)
            .set("messageId", message.messageId.toLong())
            .set("time", Timestamp.now()).build())
}

fun retrieveSDates(): QueryResults<Entity> = datastore.run(Query.newEntityQueryBuilder()
        .setOrderBy(StructuredQuery.OrderBy.asc("time"))
        .setKind("SDate").build())
