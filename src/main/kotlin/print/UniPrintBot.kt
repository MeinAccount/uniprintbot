package print

import BOT_TOKEN
import com.google.appengine.api.taskqueue.QueueFactory
import com.google.appengine.api.taskqueue.TaskOptions
import com.google.cloud.datastore.Entity
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.Document
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import remote.RemoteHost
import remote.UserStorage
import java.io.IOException
import java.io.Serializable

class UniPrintBot : TelegramLongPollingBot() {
    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage()) {
            val user = UserStorage.getUser(update.message.from.id)
            if (user == null) {
                executeSafe(SendMessage(update.message.chatId, "Permission Denied for user ${update.message.from.id}"))
            } else {
                processMessage(update.message, user)
            }

        } else if (update.hasCallbackQuery()) {
            val user = UserStorage.getUser(update.callbackQuery.from.id)
            if (user == null) {
                executeSafe(SendMessage(update.message.chatId,
                        "Permission Denied for user ${update.callbackQuery.from.id}"))
            } else {
                processCallbackQuery(update.callbackQuery, user)
            }
        }
    }

    private fun processMessage(message: Message, user: Entity) {
        if (message.hasText()) {
            when (message.text.toLowerCase()) { // process commands
                "/start" ->
                    executeSafe(SendMessage(message.chatId,
                            "Antworte auf eine PDF-Datei mit /print um diese erneut zu drucken.\n" +
                                    "*Warnung:* Dieser Bot druckt jetzt in der Fachschaft. Mit der Nutzung dieses Bots sicher ihr mit zu, dies sparsam und den üblichen Regeln entsprechend zu verwenden.")
                            .setParseMode("Markdown"))

                "/cancel" -> {
                    try {
                        executeSafe(SendChatAction(message.chatId, "typing"))
                        RemoteHost.cancelAll()
                        executeSafe(SendMessage(message.chatId, "Alle Druckaufträge wurden abgebrochen."))
                    } catch (e: IOException) {
                        executeSafe(SendMessage(message.chatId,
                                "Das Abbrechen aller Druckaufträge ist leider fehlgeschlagen! IOException"))
                    }
                }

                "/print" ->
                    if (message.isReply && message.replyToMessage.hasDocument() &&
                            validateTelegramFile(message.replyToMessage.document)) {
                        printTelegramFile(user, message.replyToMessage.document, message)
                    } else {
                        executeSafe(SendMessage(message.chatId,
                                "Antworte auf eine PDF-Datei mit /print um diese erneut zu drucken."))
                    }

                "/recheck" -> QueueFactory.getDefaultQueue().add(TaskOptions.Builder.withUrl("/notify"))

                else -> executeSafe(SendMessage(message.chatId, "Ich habe dich leider nicht verstanden."))
            }

        } else if (message.hasDocument() && validateTelegramFile(message.document)) {
            // process uploaded PDFs
            val keyboard = InlineKeyboardMarkup()
            keyboard.keyboard.add(listOf(InlineKeyboardButton("${message.document.fileName} drucken")
                    .setCallbackData("printTelegramFile")))

            executeSafe(SendMessage(message.chatId, "Bestätige den Druckvorgang:")
                    .setReplyToMessageId(message.messageId)
                    .setReplyMarkup(keyboard))
        } else {
            executeSafe(SendMessage(message.chatId, "Ich verarbeite nur PDF-Dateien."))
        }
    }


    private fun processCallbackQuery(callbackQuery: CallbackQuery, user: Entity) {
        if (callbackQuery.data == "printTelegramFile") {
            if (callbackQuery.message.hasDocument()) {
                executeSafe(EditMessageReplyMarkup().setChatId(callbackQuery.message.chatId)
                        .setMessageId(callbackQuery.message.messageId))

                printTelegramFile(user, callbackQuery.message.document, callbackQuery.message)
            } else if (callbackQuery.message.isReply && callbackQuery.message.replyToMessage.hasDocument()) {
                executeSafe(EditMessageText().setChatId(callbackQuery.message.chatId)
                        .setMessageId(callbackQuery.message.messageId)
                        .setText("Datei wird gedruckt..."))

                printTelegramFile(user, callbackQuery.message.replyToMessage.document, callbackQuery.message)
                executeSafe(DeleteMessage(callbackQuery.message.chatId, callbackQuery.message.messageId))
            }
        }

        // TODO: handle unknown or invalid stuff
    }


    private fun validateTelegramFile(document: Document) =
            document.fileName.endsWith(".pdf") && document.mimeType == "application/pdf"

    private fun printTelegramFile(user: Entity, document: Document, message: Message) {
        executeSafe(SendChatAction(message.chatId, "typing"))
        val replyTo = message.replyToMessage?.messageId ?: message.messageId

        try {
            val file = execute(GetFile().setFileId(document.fileId))
            UserStorage.logPrintJob(user, document)

            RemoteHost.printTelegramFile(user, file)
            executeSafe(SendMessage(message.chatId, "Datei wurde gedruckt!")
                    .setReplyToMessageId(replyTo))
        } catch (e: TelegramApiException) {
            e.printStackTrace()
            executeSafe(SendMessage(message.chatId,
                    "Drucken leider fehlgeschlagen! Konnte Datei nicht von Telegram abrufen.")
                    .setReplyToMessageId(replyTo))
        } catch (e: IOException) {
            e.printStackTrace()
            executeSafe(SendMessage(message.chatId, "Drucken leider fehlgeschlagen! IOException")
                    .setReplyToMessageId(replyTo))
        }
    }

    fun <T : Serializable, Method : BotApiMethod<T>> executeSafe(method: Method): T? {
        try {
            return execute(method)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }

        return null
    }


    override fun getBotToken() = BOT_TOKEN

    override fun getBotUsername() = "UniPrintBot"


    override fun clearWebhook() {
    }

    fun clearOverrideWebhook() {
        super.clearWebhook()
    }
}
