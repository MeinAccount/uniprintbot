package bot

import BOT_PATH
import com.fasterxml.jackson.databind.ObjectMapper
import org.telegram.telegrambots.api.objects.Update
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet("/$BOT_PATH")
class WebhookController : HttpServlet() {
    private val bot = UniPrintBot()

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val mapper = ObjectMapper()
        val update = mapper.readValue(req.reader, Update::class.java)
        println(update)

        val response = bot.processUpdate(update)
        println(response)

        resp.status = 200
        resp.contentType = "application/json";
        resp.characterEncoding = "UTF-8";
        mapper.writeValue(resp.outputStream, response)
    }
}
