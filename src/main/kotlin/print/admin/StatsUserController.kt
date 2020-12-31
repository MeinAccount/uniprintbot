package print.admin

import NOTIFY_RESOURCE_LIST
import com.google.cloud.Timestamp
import kotlinx.html.*
import remote.UserIliasNotificationStorage
import remote.UserStorage
import java.net.URLEncoder
import javax.servlet.annotation.HttpConstraint
import javax.servlet.annotation.ServletSecurity
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet("/stats/*")
@ServletSecurity(
    HttpConstraint(
        rolesAllowed = arrayOf("admin"),
        transportGuarantee = ServletSecurity.TransportGuarantee.CONFIDENTIAL
    )
)
class StatsUserController : StatsController() {
    override fun FlowContent.content(
        req: HttpServletRequest,
        users: Map<String, UserStat>,
        jobs: List<JobStat>,
        yearSuffix: String
    ) {
        val userID = req.pathInfo.drop(1)
        h1 {
            a("/stats$yearSuffix") {
                text("Letzte Druckaufträge von ${users[userID]?.name ?: "unbekannt"}")
            }
        }

        listJobs(users[userID]?.jobs ?: emptyList(), yearSuffix)

        userID.toIntOrNull()?.let(UserStorage::getUser)?.let { user ->
            val notifications = UserIliasNotificationStorage.getByUser(user).map {
                NotificationData(
                    it.getString("name"), it.getString("type"), it.getTimestamp("time"),
                    it.getString("hash"), it.getLong("messageId"), it.getString("url")
                )
            }.groupBy { it.dbName }

            div {
                id = "notifications"
                notifications.forEach { (dbName, messages) ->
                    val resource = NOTIFY_RESOURCE_LIST.find { it.dbName == dbName } ?: return@forEach

                    h2 { text("${resource.name} Blätter") }
                    div("center") {
                        table {
                            messages.sortedBy { it.time }.forEach {
                                tr {
                                    td {
                                        val url = URLEncoder.encode(it.url, "UTF-8")
                                        val name = URLEncoder.encode(it.name, "UTF-8")
                                        a(href = "/download-ilias?url=$url&name=$name") {
                                            text(it.name)
                                        }
                                    }
                                    td { printTime(it.time) }
                                    td { text(it.messageID) }
                                    td { text(it.hash) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val token = req.session.getAttribute("token") as String?
        val user = req.pathInfo.drop(1).toIntOrNull()?.let(UserStorage::getUser)
        val name = req.getParameter("name")
        resp.writer.println(user)
        resp.writer.println(name)

        when {
            token == null -> resp.sendRedirect("/management")
            user == null || name == null -> resp.sendError(HttpServletResponse.SC_BAD_REQUEST)
            req.getParameter("token") != token -> resp.sendError(HttpServletResponse.SC_UNAUTHORIZED)
            else -> {
                UserStorage.setName(user, name)
                resp.sendRedirect("/management?userID=${user.key.name}&field=name")
            }
        }
    }
}


internal data class NotificationData(
    val name: String, val dbName: String, val time: Timestamp,
    val hash: String, val messageID: Long, val url: String
)
