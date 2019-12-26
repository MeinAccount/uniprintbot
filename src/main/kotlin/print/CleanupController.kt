package print

import com.google.cloud.datastore.Entity
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import remote.UserStorage
import remote.datastore
import javax.servlet.annotation.HttpConstraint
import javax.servlet.annotation.ServletSecurity
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet("/cleanup")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin"),
        transportGuarantee = ServletSecurity.TransportGuarantee.CONFIDENTIAL))
class CleanupController : HttpServlet() {
    private val bot = UniPrintBot()

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val batch = datastore.newBatch()
        try {
            UserStorage.listPrintJobsOneDay().forEach {
                val userID = it.key.ancestors.firstOrNull()?.name
                if (it.contains("printedMessageID") && userID != null) {
                    println("Processing #${it.getLong("printedMessageID")} from user #$userID")
                    bot.executeSafe(DeleteMessage(userID, it.getLong("printedMessageID").toInt()))

                    if (it.contains("commandMessageID")) {
                        bot.executeSafe(DeleteMessage(userID, it.getLong("commandMessageID").toInt()))
                    }

                    batch.update(Entity.newBuilder(it)
                            .remove("printedMessageID")
                            .remove("commandMessageID")
                            .build())
                }
            }
        } finally {
            batch.submit()
        }
    }
}
