package bot

import ILIAS_PAGE_ID
import org.telegram.telegrambots.api.methods.send.SendDocument
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton
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

@WebServlet("/ilias/notify")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class IliasNotifyController : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val iliasResources = Ilias.listIliasResources("statistik", ILIAS_PAGE_ID)
        val resourceToTelegramFileId = mutableMapOf<IliasResource, String>()

        UserStorage.listNotifyUsers().forEach { user ->
            val urlSet = UserIliasNotificationStorage.getByUser(user).map { notification ->
                notification.getString("url")
            }.toSet()

            iliasResources.filter { !urlSet.contains(it.url) }.forEach { resource ->
                val name = "Statistik ${resource.name}"
                val keyboard = InlineKeyboardMarkup()
                keyboard.keyboard.add(listOf(InlineKeyboardButton("$name drucken")
                        .setCallbackData("printTelegramFile")))

                resourceToTelegramFileId.compute(resource) { _, fileId ->
                    val command = SendDocument().setChatId(user.key.name).setReplyMarkup(keyboard)
                    if (fileId == null) { // upload file to Telegram
                        command.setNewDocument(name, Ilias.download(resource.url).inputStream())
                    }

                    val message = WebhookController.bot.sendDocument(command)
                    UserIliasNotificationStorage.add(user, message.chatId, message.messageId, resource)
                    return@compute message.document.fileId
                }
            }
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        doGet(req, resp)
    }
}

