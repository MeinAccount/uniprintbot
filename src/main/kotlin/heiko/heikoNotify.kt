package heiko

import HEIKO_GROUP
import HEIKO_TODAY
import com.google.cloud.datastore.Entity
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.UnpinChatMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendSticker
import remote.datastore
import java.time.LocalDate
import javax.servlet.annotation.HttpConstraint
import javax.servlet.annotation.ServletSecurity
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet("/heiko/daily")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class HeikoTuesdayController : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        HeikoNotificationBot().apply {
            retrieveSDates().forEach {
                if (extractLocalDate(it) == LocalDate.now()) {
                    val chatId = it.getLong("chatId")
                    execute(SendMessage(chatId, HEIKO_TODAY).setParseMode("Markdown"))
                    execute(SendSticker().setSticker(positiveStickers.random()).setChatId(chatId))
                }
            }
        }
    }
}

@WebServlet("/heiko/today")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class HeikoTodayController : HttpServlet() {
    override fun doGet(req: HttpServletRequest?, resp: HttpServletResponse?) {
        HeikoNotificationBot().apply {
            execute(SendMessage(HEIKO_GROUP, HEIKO_TODAY).setParseMode("Markdown"))
            execute(SendSticker().setSticker(positiveStickers.random()).setChatId(HEIKO_GROUP))
        }
    }
}


@WebServlet("/heiko/cleanup")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class HeikoCleanupController : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        HeikoNotificationBot().apply {
            retrieveSDates().forEach {
                if (extractLocalDate(it) < LocalDate.now()) {
                    val chatId = it.getLong("chatId")
                    datastore.delete(it.key)

                    val chat = execute(GetChat(chatId))
                    if (chat.pinnedMessage.messageId == it.getInt("messageId") + 1) {
                        execute(UnpinChatMessage(chatId))
                    }
                }
            }
        }
    }
}


private fun extractLocalDate(entity: Entity) =
        LocalDate.of(entity.getInt("year"), entity.getInt("month"), entity.getInt("day"))

private fun Entity.getInt(name: String) = getLong(name).toInt()
