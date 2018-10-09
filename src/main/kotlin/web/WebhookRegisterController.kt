package web

import BOT_PATH
import BOT_TOKEN
import HEIKO_PATH
import HEIKO_TOKEN
import org.telegram.telegrambots.bots.TelegramWebhookBot
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.objects.Update
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
        PrintBotWebhook().setWebhook("https://uniprintbot.appspot.com/$BOT_PATH", null);
        HeikoBotWebhook().setWebhook("https://uniprintbot.appspot.com/$HEIKO_PATH", null);
        resp.writer.println("Success")
    }


    private class PrintBotWebhook : TelegramWebhookBot() {
        override fun getBotPath() = BOT_PATH

        override fun getBotToken() = BOT_TOKEN

        override fun getBotUsername() = "UniPrintBot"

        override fun onWebhookUpdateReceived(update: Update): BotApiMethod<*> {
            TODO("not implemented")
        }
    }

    private class HeikoBotWebhook : TelegramWebhookBot() {
        override fun getBotPath() = HEIKO_PATH

        override fun getBotUsername() = "HeikoNotificationBot"

        override fun getBotToken() = HEIKO_TOKEN

        override fun onWebhookUpdateReceived(update: Update?): BotApiMethod<*> {
            TODO("not implemented")
        }
    }
}
