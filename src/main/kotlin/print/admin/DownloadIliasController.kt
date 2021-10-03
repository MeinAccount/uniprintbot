package print.admin

import remote.Ilias
import java.net.URLDecoder
import javax.servlet.annotation.HttpConstraint
import javax.servlet.annotation.ServletSecurity
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet("/download-ilias")
@ServletSecurity(
    HttpConstraint(
        rolesAllowed = arrayOf("admin"),
        transportGuarantee = ServletSecurity.TransportGuarantee.CONFIDENTIAL
    )
)
class DownloadIliasController : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val url = req.getParameter("url")
        val body = Ilias.downloadURL(url).body()

        if (body != null) {
            resp.contentType = body.contentType()?.toString() ?: "application/pdf"
            resp.setContentLength(body.contentLength().toInt())
            req.getParameter("name")?.let {
                resp.setHeader("Content-Disposition", """inline; filename="${URLDecoder.decode(it, "UTF-8")}"""")
            }

            body.byteStream().copyTo(resp.outputStream)
        }
    }
}