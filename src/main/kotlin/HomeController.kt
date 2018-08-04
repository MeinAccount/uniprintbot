import com.fasterxml.jackson.databind.ObjectMapper
import org.telegram.telegrambots.api.objects.Update
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet(name = "Hello", value = ["/"])
class HomeController : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.writer.write("Hello, World!")
    }
}

@WebServlet("/$BOT_PATH/UniPrintBot")
class WebhookController : HttpServlet() {
    private val bot = UniPrintBot()

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val mapper = ObjectMapper()
        val update = mapper.readValue(req.reader, Update::class.java)
        println(update)

        val response = bot.onUpdateReceived(update)
        println(response)

        resp.status = 200
        resp.contentType = "application/json";
        resp.characterEncoding = "UTF-8";
        mapper.writeValue(resp.outputStream, response)
    }
}
