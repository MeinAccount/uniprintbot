import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramWebhookBot
import javax.servlet.annotation.HttpConstraint
import javax.servlet.annotation.ServletSecurity
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet("/webhook/register")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class WebhookRegisterController : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val bot = BotWebhook()
        bot.setWebhook("https://uniprintbot.appspot.com/$BOT_PATH/UniPrintBot", null);
        resp.writer.println("Success")
    }

    private class BotWebhook : TelegramWebhookBot() {
        override fun getBotToken() = BOT_TOKEN

        override fun getBotUsername() = "UniPrintBot"

        override fun getBotPath() = "UniPrintBot"

        override fun onWebhookUpdateReceived(update: Update?): BotApiMethod<*> {
            TODO("not implemented")
        }
    }
}
