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
class HeikoDailyController : HttpServlet() {
    private val bot = lazy { HeikoNotificationBot() }
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        retrieveSDates().forEach {
            if (extractLocalDate(it) == LocalDate.now()) {
                val chatId = it.getLong("chatId")

                bot.value.execute(SendMessage(chatId, HEIKO_TODAY).setParseMode("Markdown"))
                bot.value.execute(SendSticker().setSticker(positiveStickers.random()).setChatId(chatId))
            }
        }
    }
}

@WebServlet("/heiko/today")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class HeikoTodayController : HttpServlet() {
    private val bot = lazy { HeikoNotificationBot() }
    override fun doGet(req: HttpServletRequest?, resp: HttpServletResponse?) {
        HeikoNotificationBot().apply {
            bot.value.execute(SendMessage(HEIKO_GROUP, HEIKO_TODAY).setParseMode("Markdown"))
            bot.value.execute(SendSticker().setSticker(positiveStickers.random()).setChatId(HEIKO_GROUP))
        }
    }
}


@WebServlet("/heiko/cleanup")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class HeikoCleanupController : HttpServlet() {
    private val bot = lazy { HeikoNotificationBot() }
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        retrieveSDates().forEach {
            if (extractLocalDate(it) < LocalDate.now()) {
                val chatId = it.getLong("chatId")
                datastore.delete(it.key)

                val chat = bot.value.execute(GetChat(chatId))
                if (chat.pinnedMessage.messageId == it.getInt("messageId") + 1) {
                    bot.value.execute(UnpinChatMessage(chatId))
                }
            }
        }
    }
}


fun extractLocalDate(entity: Entity): LocalDate =
        LocalDate.of(entity.getInt("year"), entity.getInt("month"), entity.getInt("day"))

private fun Entity.getInt(name: String) = getLong(name).toInt()
