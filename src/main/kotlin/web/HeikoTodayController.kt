package web

import HEIKO_GROUP
import HEIKO_TEXT
import HEIKO_TOKEN
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
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
        val bot = HeikoNotificationBot()
        bot.execute(SendMessage(HEIKO_GROUP, HEIKO_TEXT).setParseMode("Markdown"))
    }
}

class HeikoNotificationBot : TelegramLongPollingBot() {
    override fun onUpdateReceived(update: Update) {
        println(update)
    }

    override fun getBotUsername() = "HeikoNotificationBot"

    override fun getBotToken() = HEIKO_TOKEN
}
