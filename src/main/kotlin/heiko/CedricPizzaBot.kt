package heiko

import CEDRIC_GROUP
import CEDRIC_MESSAGE
import CEDRIC_TOKEN
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
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

@WebServlet("/cedric/today")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class CedricPizza : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        CedricPizzaBot().execute(SendMessage(CEDRIC_GROUP, CEDRIC_MESSAGE))
    }
}
