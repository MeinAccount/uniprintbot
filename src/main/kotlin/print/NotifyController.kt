package print

import NOTIFY_RESOURCE_LIST
import com.google.cloud.datastore.Entity
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import remote.Ilias
import remote.IliasResource
import remote.UserIliasNotificationStorage
import remote.UserStorage
import javax.servlet.annotation.HttpConstraint
import javax.servlet.annotation.ServletSecurity
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet("/notify")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin"),
        transportGuarantee = ServletSecurity.TransportGuarantee.CONFIDENTIAL))
class NotifyController : HttpServlet() {
    private val bot = UniPrintBot()
    private var resources = emptyMap<String, List<IliasResource>>()

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        // list current resources and fetch current etags / hashes
        resources = NOTIFY_RESOURCE_LIST.associate { origin ->
            val previousResources = resources[origin.dbName] ?: emptyList()
            return@associate origin.dbName to origin.retrieve().mapNotNull { (name, url) ->
                Ilias.downloadRefresh(origin.dbName, name, url, previousResources.firstOrNull { it.url == url })
            }
        }

        // process for all users
        UserStorage.listUsers().forEach { user ->
            println("Processing ${user.key.name} ${user.getString("name")}")
            val notifications = UserIliasNotificationStorage.getByUser(user)
                    .map { it.getString("url") to it }.toMap()
            resources.filterKeys {
                user.getOptionalBool("notify$it") ?: false
            }.values.flatten().forEach { resource ->
                val notification = notifications[resource.url]
                when {
                    notification == null -> sendNew(resource, user)
                    notification.getString("hash") != resource.hash -> {
                        // require hash to remain changed over two checks
                        if (notification.getOptionalString("hash2") != resource.hash) {
                            println("${resource.type} ${resource.name} prechange from " +
                                    "${notification.getString("hash")} to ${resource.hash} " +
                                    "intermediary ${notification.getOptionalString("hash2")}")
                            UserIliasNotificationStorage.updateHash2(notification, resource.hash)
                        } else {
                            sendUpdate(resource, notification, user)
                        }
                    }

                    else -> {
                        if (notification.contains("hash2")) {
                            UserIliasNotificationStorage.removeHash2(notification)
                            println("${resource.type} ${resource.name} reverted")
                        } else {
                            println("${resource.type} ${resource.name} unchanged")
                        }
                    }
                }
            }
        }
    }

    private fun sendNew(resource: IliasResource, user: Entity) {
        print("${resource.type} ${resource.name} new message ")
        val command = SendDocument().setChatId(user.key.name)
        resource.telegram.attach(resource.getPrintName(), command)

        try {
            val message = bot.execute(command)
            UserIliasNotificationStorage.add(user, message, resource)
            resource.processMessage(message)
            println(message.messageId)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    private fun sendUpdate(resource: IliasResource, notification: Entity, user: Entity) {
        print("${resource.type} ${resource.name} changed from ${notification.getString("hash")} " +
                "in ${notification.getLong("messageId")} to ${resource.hash} message ")
        val command = SendDocument().setChatId(user.key.name)
                .setReplyToMessageId(notification.getLong("messageId").toInt())
        resource.telegram.attach(resource.getPrintName(), command)

        try {
            val message = bot.execute(command)
            UserIliasNotificationStorage.update(notification, message, resource)
            resource.processMessage(message)
            println(message.messageId)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }


    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        doGet(req, resp)
    }
}

fun Entity.getOptionalString(name: String) = if (contains(name)) getString(name) else null
fun Entity.getOptionalBool(name: String) = if (contains(name)) getBoolean(name) else null
