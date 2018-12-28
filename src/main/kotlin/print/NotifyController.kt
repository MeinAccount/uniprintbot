package print

import NOTIFY_RESOURCE_LIST
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
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class NotifyController : HttpServlet() {
    private val bot = UniPrintBot()
    private var resources = emptyMap<String, List<IliasResource>>()

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        // list current resources and fetch current etags / hashes
        resources = NOTIFY_RESOURCE_LIST().mapValues { entry ->
            val previousResources = resources[entry.key] ?: emptyList()
            return@mapValues entry.value.mapNotNull { (name, url) ->
                Ilias.downloadRefresh(entry.key, name, url, previousResources.firstOrNull { it.url == url })
            }
        }

        // process for all users
        UserStorage.listUsers().forEach { user ->
            println("Processing ${user.key.name} ${user.getString("name")}")
            val notifications = UserIliasNotificationStorage.getByUser(user)
                    .map { it.getString("url") to it }.toMap()
            resources.filterKeys { user.getBoolean("notify$it") }.values.flatten().forEach { resource ->
                val notification = notifications[resource.url]
                if (notification == null) {
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
                } else if (notification.getString("hash") != resource.hash) {
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
                } else {
                    println("${resource.type} ${resource.name} unchanged")
                }
            }
        }
    }
}
