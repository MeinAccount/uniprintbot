package print.admin

import NOTIFY_RESOURCE_LIST
import com.google.cloud.datastore.Entity
import kotlinx.html.*
import remote.UserStorage
import java.util.*
import javax.servlet.annotation.HttpConstraint
import javax.servlet.annotation.ServletSecurity
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet("/management")
@ServletSecurity(
    HttpConstraint(
        rolesAllowed = arrayOf("admin"),
        transportGuarantee = ServletSecurity.TransportGuarantee.CONFIDENTIAL
    )
)
class ManagementController : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val users = UserStorage.listUsers().asSequence().map {
            UserData(it.key.name, it.getString("name"), it)
        }.toList()

        val token = req.session.getAttribute("token") as String?
            ?: UUID.randomUUID().toString().also {
                req.session.setAttribute("token", it)
            }

        val orderBy = req.getParameter("orderBy") ?: "name"
        req.getParameter("desc") != null
        val highlightUserID: String? = req.getParameter("userID")
        val highlightField: String? = req.getParameter("field")

        htmlTemplate(resp, "@UniPrintBot Verwaltung") {
            header()
            div("center") {
                table {
                    tr {
                        th {
                            a(href = "/management?orderBy=id") { text("ID") }
                        }
                        th {
                            a(href = "/management") { text("Name") }
                        }

                        NOTIFY_RESOURCE_LIST.map {
                            th {
                                a(href = "/management?orderBy=${it.dbName}") { text(it.name) }
                            }
                        }
                    }

                    users.sortedWith(when (orderBy) {
                        "id" -> compareBy { it.id }
                        "name" -> compareBy { it.name }
                        else -> compareByDescending<UserData> { it.hasNotify(orderBy) }.thenBy { it.name }
                    }).map { user ->
                        tr {
                            td {
                                a(href = "/stats/${user.id}#notifications") { text(user.id) }
                            }
                            td(if (highlightUserID == user.id && highlightField == "name") "edit-column highlight" else "edit-column") {
                                span { text(user.name) }
                                form(action = "/stats/${user.id}", method = FormMethod.post) {
                                    style = "display: none;"

                                    hiddenInput("token", token)
                                    input(type = InputType.text, name = "name") {
                                        value = user.name
                                    }
                                }
                            }

                            NOTIFY_RESOURCE_LIST.map {
                                td(if (highlightUserID == user.id && highlightField == it.dbName) "highlight" else "") {
                                    form(action = "/management", method = FormMethod.post) {
                                        hiddenInput("token", token)
                                        hiddenInput("userID", user.id)
                                        hiddenInput("field", it.dbName)
                                        button(type = ButtonType.submit) {
                                            unsafe { +if (user.hasNotify(it.dbName)) "&#9745;" else "&#9744;" }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            script {
                unsafe {
                    //language=JavaScript 1.8
                    +"""
document.querySelectorAll('.edit-column').forEach(col => {
    col.addEventListener('dblclick', event => {
        event.preventDefault();
        col.children[0].style.display = 'none';
        col.children[1].style.display = 'block';
    });
});
setTimeout(() => {
    document.querySelectorAll('.highlight').forEach(el => el.classList.remove('highlight'));
    window.history.replaceState({}, '@UniPrintBot Verwaltung', '/management');
}, 10000);"""
                }
            }
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val token = req.session.getAttribute("token") as String?
        val user = req.getParameter("userID")?.toLongOrNull()?.let(UserStorage::getUser)
        val field = req.getParameter("field")

        when {
            token == null -> resp.sendRedirect("/management")
            user == null || field == null -> resp.sendError(HttpServletResponse.SC_BAD_REQUEST)
            req.getParameter("token") != token -> resp.sendError(HttpServletResponse.SC_UNAUTHORIZED)
            else -> {
                UserStorage.toggleUserNotify(user, "notify$field")
                resp.sendRedirect("/management?userID=${user.key.name}&field=$field")
            }
        }
    }
}


private data class UserData(val id: String, val name: String, private val entity: Entity) {
    fun hasNotify(dbName: String): Boolean {
        val key = "notify$dbName"
        return entity.contains(key) && entity.getBoolean(key)
    }
}