package web

import BOT_TOKEN
import ILIAS_PAGE_ID
import com.google.appengine.api.taskqueue.QueueFactory
import com.google.appengine.api.taskqueue.TaskOptions
import com.google.cloud.datastore.Entity
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.methods.GetFile
import org.telegram.telegrambots.api.methods.send.SendChatAction
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.Document
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.exceptions.TelegramApiException
import remote.*
import java.io.Serializable

open class PollingUniPrintBot : TelegramLongPollingBot() {
    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage()) {
            val user = UserStorage.getUser(update.message.from.id)
            if (user == null) {
                execute(SendMessage(update.message.chatId, "Permission Denied for user ${update.message.from.id}"))
            } else {
                processMessage(update.message, user)
            }
        } else if (update.hasCallbackQuery()) {
            val user = UserStorage.getUser(update.callbackQuery.from.id)
            if (user == null) {
                execute(SendMessage(update.message.chatId, "Permission Denied for user ${update.callbackQuery.from.id}"))
            } else {
                processCallbackQuery(update.callbackQuery, user)
            }
        }
    }

    private fun processMessage(message: Message, user: Entity) {
        if (message.hasText()) {
            when (message.text.toLowerCase()) { // process commands
                "/start" ->
                    execute(SendMessage(message.chatId,
                            "Sende mir eine PDF-Datei oder drucke Statistik-Blätter mit /statistik.\n" +
                                    "Antworte auf eine PDF-Datei mit /print um diese erneut zu drucken."))
                "/cancel" -> {
                    execute(SendChatAction(message.chatId, "typing"))
                    RemoteHost.cancelAll()
                    executeSafe(SendMessage(message.chatId, "Alle Druckaufträge wurden abgebrochen."))
                }

                "/print" ->
                    if (message.isReply && message.replyToMessage.hasDocument() &&
                            validateTelegramFile(message.replyToMessage.document)) {
                        execute(SendChatAction(message.chatId, "typing"))

                        printTelegramFile(user, message.replyToMessage.document)
                        executeSafe(SendMessage(message.chatId, "Datei wurde gedruckt!")
                                .setReplyToMessageId(message.replyToMessage.messageId))
                    } else {
                        execute(SendMessage(message.chatId,
                                "Antworte auf eine PDF-Datei mit /print um diese erneut zu drucken."))
                    }

                "/statistik" -> {
                    execute(SendChatAction(message.chatId, "typing"))

                    val iliasResources = Ilias.listIliasResources("statistik", ILIAS_PAGE_ID)
                    IliasResourceStorage.save(user, iliasResources)

                    val response = execute(SendMessage(message.chatId,
                            "Welche Dateien sollen gedruckt werden?")
                            .setReplyMarkup(getIliasResourcesKeyboard(iliasResources)))
                    IliasResourceStorage.updateMessage(response.chatId, response.messageId, iliasResources)
                }
                "/recheck" -> QueueFactory.getDefaultQueue().add(TaskOptions.Builder.withUrl("/ilias/notify"))

                else -> execute(SendMessage(message.chatId, "Ich habe dich leider nicht verstanden."))
            }

        } else if (message.hasDocument() && validateTelegramFile(message.document)) {
            // process uploaded PDFs
            val keyboard = InlineKeyboardMarkup()
            keyboard.keyboard.add(listOf(InlineKeyboardButton("${message.document.fileName} drucken")
                    .setCallbackData("printTelegramFile")))

            execute(SendMessage(message.chatId, "Bestätige den Druckvorgang:")
                    .setReplyToMessageId(message.messageId)
                    .setReplyMarkup(keyboard))
        } else {
            execute(SendMessage(message.chatId, "Ich verarbeite nur PDF-Dateien."))
        }
    }


    private fun processCallbackQuery(callbackQuery: CallbackQuery, user: Entity) {
        when {
            callbackQuery.data == "printIliasResources" -> {
                val iliasResources = IliasResourceStorage.get(user,
                        callbackQuery.message.chatId, callbackQuery.message.messageId)

                val selected = iliasResources.filter { it.selected }
                when (selected.size) {
                    0 -> execute(AnswerCallbackQuery().setCallbackQueryId(callbackQuery.id))
                    1 -> printIliasResources(callbackQuery, user, iliasResources,
                            "${selected.single().getPrintName()} wird gedruckt...")
                    else -> {
                        val first = selected.dropLast(1).joinToString(", ") { it.getPrintName() }
                        printIliasResources(callbackQuery, user, iliasResources,
                                "$first und ${selected.last().getPrintName()} werden gedruckt...")
                    }
                }
            }

            callbackQuery.data == "printTelegramFile" ->
                if (callbackQuery.message.hasDocument()) {
                    executeSafe(EditMessageReplyMarkup().setChatId(callbackQuery.message.chatId)
                            .setMessageId(callbackQuery.message.messageId))

                    printTelegramFile(user, callbackQuery.message.document)
                    executeSafe(SendMessage(callbackQuery.message.chatId, "Datei wurde gedruckt!")
                            .setReplyToMessageId(callbackQuery.message.messageId))
                } else if (callbackQuery.message.isReply && callbackQuery.message.replyToMessage.hasDocument()) {
                    executeSafe(EditMessageText().setChatId(callbackQuery.message.chatId)
                            .setMessageId(callbackQuery.message.messageId)
                            .setText("Datei wird gedruckt..."))

                    printTelegramFile(user, callbackQuery.message.replyToMessage.document)
                    executeSafe(SendMessage(callbackQuery.message.chatId, "Datei wurde gedruckt!")
                            .setReplyToMessageId(callbackQuery.message.replyToMessage.messageId))
                }

            callbackQuery.data.startsWith("toggle|") -> {
                val resourceId = callbackQuery.data.drop(7).toLong()
                val iliasResources = IliasResourceStorage.get(user,
                        callbackQuery.message.chatId, callbackQuery.message.messageId)

                iliasResources.firstOrNull { it.entity?.key?.id == resourceId }?.let { resource ->
                    resource.selected = !resource.selected
                }
                IliasResourceStorage.updateSelected(iliasResources)

                executeSafe(AnswerCallbackQuery().setCallbackQueryId(callbackQuery.id))
                executeSafe(EditMessageReplyMarkup()
                        .setChatId(callbackQuery.message.chatId)
                        .setMessageId(callbackQuery.message.messageId)
                        .setReplyMarkup(getIliasResourcesKeyboard(iliasResources)))
            }
        }

        // TODO: handle unknown or invalid stuff
    }


    private fun validateTelegramFile(document: Document) =
            document.fileName.endsWith(".pdf") && document.mimeType == "application/pdf"

    private fun printTelegramFile(user: Entity, document: Document) {
        val file = execute(GetFile().setFileId(document.fileId))
        RemoteHost.printTelegramFile(user, file)
        UserStorage.logPrintJob(user, document)
    }

    private fun printIliasResources(callbackQuery: CallbackQuery, user: Entity,
                                    iliasResources: List<IliasResource>, text: String) {
        executeSafe(EditMessageText()
                .setChatId(callbackQuery.message.chatId)
                .setMessageId(callbackQuery.message.messageId)
                .setText(text))

        RemoteHost.printIliasResources(user, iliasResources)
        IliasResourceStorage.delete(iliasResources)
        iliasResources.filter { it.selected }.forEach { UserStorage.logPrintJob(user, it) }

        executeSafe(SendMessage(callbackQuery.message.chatId, "Dateien wurden gedruckt!")
                .setReplyToMessageId(callbackQuery.message.messageId))
    }

    fun <T : Serializable, Method : BotApiMethod<T>> executeSafe(method: Method): T? {
        try {
            return execute(method)
        } catch (e: TelegramApiException) {
            System.err.println(e)
        }

        return null
    }


    private fun getIliasResourcesKeyboard(iliasResources: List<IliasResource>): InlineKeyboardMarkup {
        val buttons = iliasResources.map {
            InlineKeyboardButton("${if (it.selected) "☒" else "☐"} ${it.name}")
                    .setCallbackData("toggle|${it.entity?.key?.id}")
        }.plus(InlineKeyboardButton("Drucken!").setCallbackData("printIliasResources"))

        return InlineKeyboardMarkup().setKeyboard(buttons.chunked(3))
    }

    override fun getBotToken() = BOT_TOKEN

    override fun getBotUsername() = "UniPrintBot"
}


class UniPrintBot : PollingUniPrintBot() {
    override fun clearWebhook() {
    }
}
