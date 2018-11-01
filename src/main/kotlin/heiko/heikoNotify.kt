package heiko

import HEIKO_GROUP
import HEIKO_TODAY
import HEIKO_TUESDAY
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendSticker
import javax.servlet.annotation.HttpConstraint
import javax.servlet.annotation.ServletSecurity
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet("/heiko/tuesday")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class HeikoTuesdayController : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        HeikoNotificationBot().apply {
            execute(SendMessage(HEIKO_GROUP, HEIKO_TUESDAY.random()).setParseMode("Markdown"))
            execute(SendSticker().setSticker(negativeStickers.random()).setChatId(HEIKO_GROUP))
        }
    }
}

@WebServlet("/heiko/today")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class HeikoTodayController : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        HeikoNotificationBot().apply {
            execute(SendMessage(HEIKO_GROUP, HEIKO_TODAY).setParseMode("Markdown"))
            execute(SendSticker().setSticker(positiveStickers.random()).setChatId(HEIKO_GROUP))
        }
    }
}
