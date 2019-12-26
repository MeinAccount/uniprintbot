package print.admin

import BOT_TOKEN
import org.telegram.telegrambots.meta.api.methods.GetFile
import print.UniPrintBot
import javax.servlet.annotation.HttpConstraint
import javax.servlet.annotation.ServletSecurity
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet("/download/*")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin"),
        transportGuarantee = ServletSecurity.TransportGuarantee.CONFIDENTIAL))
class DownloadFileController : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val bot = UniPrintBot()

        val file = bot.execute(GetFile().setFileId(req.pathInfo.drop(1)))
        resp.sendRedirect("https://api.telegram.org/file/bot$BOT_TOKEN/${file.filePath}")
    }
}
