package heiko

import CEDRIC_GROUP
import CEDRIC_MESSAGE
import CEDRIC_TOKEN
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import java.time.LocalDate
import javax.servlet.annotation.HttpConstraint
import javax.servlet.annotation.ServletSecurity
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class CedricPizzaBot : TelegramLongPollingBot() {
    override fun onUpdateReceived(update: Update) {
    }

    override fun getBotUsername() = "CedricPizzaBot"
    override fun getBotToken() = CEDRIC_TOKEN
}

@WebServlet("/cedric/daily")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class CedricDailyController : HttpServlet() {
    private val bot = lazy { CedricPizzaBot() }
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        retrieveSDates().forEach {
            if (extractLocalDate(it) == LocalDate.now()) {
                bot.value.execute(SendMessage(it.getLong("chatId"), CEDRIC_MESSAGE))
            }
        }
    }
}

@WebServlet("/cedric/today")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class CedricTodayController : HttpServlet() {
    private val bot = lazy { CedricPizzaBot() }
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        bot.value.execute(SendMessage(CEDRIC_GROUP, CEDRIC_MESSAGE))
    }
}
