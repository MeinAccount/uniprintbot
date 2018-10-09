package web

import BOT_PATH
import HEIKO_PATH
import com.fasterxml.jackson.databind.ObjectMapper
import heiko.HeikoNotificationBot
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update
import print.UniPrintBot
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

abstract class AbstractWebhookController(private val bot: TelegramLongPollingBot) : HttpServlet() {
    private val mapper = ObjectMapper()

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val update = mapper.readValue(req.reader, Update::class.java)
        println(update)

        bot.onUpdateReceived(update)
    }
}

@WebServlet("/$BOT_PATH")
class PrintWebhookController : AbstractWebhookController(UniPrintBot())

@WebServlet("/$HEIKO_PATH")
class HeikoWebhookController : AbstractWebhookController(HeikoNotificationBot())
