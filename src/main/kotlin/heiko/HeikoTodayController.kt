package heiko

import HEIKO_GROUP
import HEIKO_TEXT
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import javax.servlet.annotation.HttpConstraint
import javax.servlet.annotation.ServletSecurity
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet("/heiko/today")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class HeikoTodayController : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        HeikoNotificationBot().execute(SendMessage(HEIKO_GROUP, HEIKO_TEXT).setParseMode("Markdown"))
    }
}
