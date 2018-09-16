package web

import BOT_PATH
import com.fasterxml.jackson.databind.ObjectMapper
import org.telegram.telegrambots.meta.api.objects.Update
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet("/$BOT_PATH")
class WebhookController : HttpServlet() {
    companion object {
        internal val bot = UniPrintBot()
        private val mapper = ObjectMapper()
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val update = mapper.readValue(req.reader, Update::class.java)
        println(update)

        bot.onUpdateReceived(update)
    }
}
