package print

import BOT_TOKEN
import NOTIFY_RESOURCE_LIST
import com.google.appengine.api.taskqueue.QueueFactory
import com.google.appengine.api.taskqueue.TaskOptions
import com.google.cloud.datastore.Entity
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
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
import java.util.*

class UniPrintBot : TelegramLongPollingBot() {
    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage()) {
            val user = UserStorage.getUser(update.message.from.id)
            if (user == null) {
                executeSafe(
                    SendMessage(
                        update.message.chatId.toString(),
                        "Permission Denied for user ${update.message.from.id}"
                    )
                )
            } else {
                processMessage(update.message, user)
            }

        } else if (update.hasCallbackQuery()) {
            UserStorage.getUser(update.callbackQuery.from.id)?.let {
                processCallbackQuery(update.callbackQuery, it)
            }
        }
    }

    private fun processMessage(message: Message, user: Entity) {
        if (message.hasText()) {
            when (message.text.lowercase(Locale.GERMANY)) { // process commands
                "/start" -> {
                    executeSafe(SendMessage(
                        message.chatId.toString(),
                        "Antworte auf eine PDF-Datei mit /print um diese erneut zu drucken.\n" +
                                "*Warnung:* Dieser Bot druckt in der Fachschaft. Mit der Nutzung dieses Bots sicher ihr mir zu, dies sparsam und den üblichen Regeln entsprechend zu verwenden."
                    ).apply {
                        parseMode = "Markdown"
                    })
                    executeSafe(
                        SendMessage(
                            message.chatId.toString(),
                            "Willst du automatisch Übungsblätter bekommen?"
                        ).apply { replyMarkup = getEditKeyboard(user) })
                }

                "/cancel" -> {
                    try {
                        executeSafe(SendChatAction(message.chatId.toString(), "typing"))
                        RemoteHost.cancelAll()
                        executeSafe(SendMessage(message.chatId.toString(), "Alle Druckaufträge wurden abgebrochen."))
                    } catch (e: IOException) {
                        executeSafe(
                            SendMessage(
                                message.chatId.toString(),
                                "Das Abbrechen aller Druckaufträge ist leider fehlgeschlagen! IOException"
                            )
                        )
                    }
                }

                "/print" ->
                    if (message.isReply && message.replyToMessage.hasDocument() &&
                        validateTelegramFile(message.replyToMessage.document)
                    ) {
                        executeSafe(SendChatAction(message.chatId.toString(), "typing"))

                        val document = message.replyToMessage.document
                        val (text, success) = printTelegramFile(user, document)
                        val printedMessage = executeSafe(SendMessage(message.chatId.toString(), text).apply {
                            replyToMessageId = message.messageId
                        })
                        if (success) {
                            UserStorage.logPrintJob(user, document, printedMessage, message)
                        }
                    } else {
                        executeSafe(
                            SendMessage(
                                message.chatId.toString(),
                                "Antworte auf eine PDF-Datei mit /print um diese erneut zu drucken."
                            )
                        )
                    }

                "/edit" ->
                    executeSafe(
                        SendMessage(message.chatId.toString(), "Welche Blätter willst du bekommen?").apply {
                            replyToMessageId = message.messageId;
                            replyMarkup = getEditKeyboard(user)
                        })

                "/recheck" -> {
                    QueueFactory.getDefaultQueue().add(TaskOptions.Builder.withUrl("/notify"))
                    executeSafe(SendChatAction(message.chatId.toString(), "upload_document"))
                }

                else -> executeSafe(SendMessage(message.chatId.toString(), "Ich habe dich leider nicht verstanden."))
            }

        } else if (message.hasDocument() && validateTelegramFile(message.document)) {
            // process uploaded PDFs
            executeSafe(SendMessage(message.chatId.toString(), "Bestätige den Druckvorgang:").apply {
                replyToMessageId = message.messageId
                replyMarkup =
                    InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton("${message.document.fileName} drucken").apply {
                        callbackData = "printTelegramFile"
                    })))
            })
        } else {
            executeSafe(SendMessage(message.chatId.toString(), "Ich verarbeite nur PDF-Dateien."))
        }
    }


    private fun processCallbackQuery(callbackQuery: CallbackQuery, user: Entity) {
        if (callbackQuery.data == "printTelegramFile" && callbackQuery.message.isReply && callbackQuery.message.replyToMessage.hasDocument()) {
            executeSafe(EditMessageText(callbackQuery.message.chatId.toString()).apply {
                messageId = callbackQuery.message.messageId
                text = "Datei wird gedruckt..."
            })

            val document = callbackQuery.message.replyToMessage.document
            val (textRes, success) = printTelegramFile(user, document)
            executeSafe(EditMessageText(callbackQuery.message.chatId.toString()).apply {
                messageId = callbackQuery.message.messageId
                text = textRes
            })

            if (success) {
                UserStorage.logPrintJob(user, document, callbackQuery.message, null)
            }
        } else if (callbackQuery.data.startsWith("toggleNotify|")) {
            val newUser = UserStorage.toggleUserNotify(user, "notify" + callbackQuery.data.drop(13))
            executeSafe(AnswerCallbackQuery(callbackQuery.id))
            executeSafe(EditMessageReplyMarkup().apply {
                chatId = callbackQuery.message.chatId.toString()
                messageId = callbackQuery.message.messageId
                replyMarkup = getEditKeyboard(newUser)
            }
            )
        } else if (callbackQuery.data == "notifyUpdate") {
            QueueFactory.getDefaultQueue().add(TaskOptions.Builder.withUrl("/notify"))

            executeSafe(DeleteMessage(callbackQuery.message.chatId.toString(), callbackQuery.message.messageId))
            callbackQuery.message.replyToMessage?.let {
                executeSafe(DeleteMessage(callbackQuery.message.chatId.toString(), it.messageId))
            }

            executeSafe(SendChatAction(callbackQuery.message.chatId.toString(), "upload_document"))
        }
    }


    private fun getEditKeyboard(user: Entity): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            NOTIFY_RESOURCE_LIST.map {
                val notify = user.getBoolean("notify" + it.dbName)
                listOf(
                    InlineKeyboardButton("${if (notify) "☑" else "☐"} ${it.name}").apply {
                        callbackData = "toggleNotify|" + it.dbName
                    }
                )
            }.plusElement(
                listOf(InlineKeyboardButton("Jetzt neue Blätter abrufen!").apply {
                    callbackData = "notifyUpdate"
                })
            )
        )
    }


    private fun validateTelegramFile(document: Document) =
        document.fileName.endsWith(".pdf") && document.mimeType == "application/pdf"

    private fun printTelegramFile(user: Entity, document: Document): Pair<String, Boolean> {
        try {
            val file = execute(GetFile(document.fileId))
            RemoteHost.printTelegramFile(user, file)

            return "Datei wurde gedruckt!" to true
        } catch (e: TelegramApiException) {
            e.printStackTrace()
            return "Drucken leider fehlgeschlagen! Konnte Datei nicht von Telegram abrufen." to false
        } catch (e: IOException) {
            e.printStackTrace()
            return "Drucken leider fehlgeschlagen! IOException" to false
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
