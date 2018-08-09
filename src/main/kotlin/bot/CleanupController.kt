package bot

import com.google.cloud.Timestamp
import com.google.cloud.datastore.Query
import com.google.cloud.datastore.StructuredQuery
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.exceptions.TelegramApiException
import remote.datastore
import java.time.LocalDateTime
import javax.servlet.annotation.HttpConstraint
import javax.servlet.annotation.ServletSecurity
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet("/cleanup")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class CleanupController : HttpServlet() {
    override fun doGet(req: HttpServletRequest?, resp: HttpServletResponse?) {
        val query = Query.newEntityQueryBuilder()
                .setKind("IliasResource")
                .setFilter(StructuredQuery.PropertyFilter.le("lastUsed",
                        Timestamp.of(java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(7)))
                )).build()

        val processed = mutableSetOf<Pair<Long, Long>>()
        datastore.run(query).forEach { entity ->
            val chatId = entity.getLong("chatId")
            val messageId = entity.getLong("messageId")
            println(entity)

            if (processed.add(chatId to messageId)) {
                try {
                    WebhookController.bot.execute(EditMessageText().also { update ->
                        update.chatId = chatId.toString()
                        update.messageId = messageId.toInt()
                        update.text = "Abgebrochen"
                    })
                } catch (e: TelegramApiException) {
                    println(e)
                }
            }

            datastore.delete(entity.key)
        }
    }
}
