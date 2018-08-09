package bot

import BOT_TOKEN
import ILIAS_PAGE_ID
import com.google.cloud.datastore.Entity
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery
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
import remote.*

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
                "/print" ->
                    if (message.isReply && message.replyToMessage.hasDocument() &&
                            validateTelegramFile(message.replyToMessage.document)) {
                        execute(SendChatAction(message.chatId, "typing"))

                        printTelegramFile(user, message.replyToMessage.document.fileId)
                        execute(SendMessage(message.chatId, "Datei wurde gedruckt!").also { response ->
                            response.replyToMessageId = message.replyToMessage.messageId
                        })
                    } else {
                        execute(SendMessage(message.chatId,
                                "Antworte auf eine PDF-Datei mit /print um diese erneut zu drucken."))
                    }

                "/statistik" -> {
                    execute(SendChatAction(message.chatId, "typing"))

                    val iliasResources = Ilias.listIliasResources("statistik", ILIAS_PAGE_ID)
                    IliasResourceStorage.save(user, iliasResources)

                    val response = execute(SendMessage(message.chatId,
                            "Welche Dateien sollen gedruckt werden?").also { response ->
                        response.replyMarkup = getIliasResourcesKeyboard(iliasResources)
                    })
                    IliasResourceStorage.updateMessage(response.chatId, response.messageId, iliasResources)
                }
                else -> execute(SendMessage(message.chatId, "Ich habe dich leider nicht verstanden."))
            }

        } else if (message.hasDocument() && validateTelegramFile(message.document)) {
            // process uploaded PDFs
            val keyboard = InlineKeyboardMarkup()
            keyboard.keyboard.add(listOf(InlineKeyboardButton("${message.document.fileName} drucken")
                    .setCallbackData("printTelegramFile")))
            execute(SendMessage(message.chatId, "Bestätige den Druckvorgang:").also { response ->
                response.replyToMessageId = message.messageId
                response.replyMarkup = keyboard
            })

            UserStorage.saveUpload(user, message.document)
        } else {
            execute(SendMessage(message.chatId, "Ich verarbeite nur PDF-Dateien."))
        }
    }


    private fun processCallbackQuery(callbackQuery: CallbackQuery, user: Entity) {
        when {
            callbackQuery.data == "printIliasFiles" -> {
                val iliasResources = IliasResourceStorage.get(user, callbackQuery.message.chatId, callbackQuery.message.messageId)
                val selected = iliasResources.filter { it.selected }
                when (selected.size) {
                    0 -> execute(AnswerCallbackQuery().also { it.callbackQueryId = callbackQuery.id })
                    1 -> printIliasResources(callbackQuery, user, iliasResources, "${selected.single().name} wird gedruckt...")
                    else -> {
                        val first = selected.dropLast(1).joinToString(", ", transform = IliasResource::name)
                        printIliasResources(callbackQuery, user, iliasResources, "$first und ${selected.last().name} werden gedruckt...")
                    }
                }

                return
            }

            callbackQuery.data == "printTelegramFile" ->
                if (callbackQuery.message.hasDocument()) {
                    printTelegramFile(user, callbackQuery.message.document.fileId)

                    execute(AnswerCallbackQuery().also { it.callbackQueryId = callbackQuery.id })
                    execute(SendMessage(callbackQuery.message.chatId, "Datei wurde gedruckt!").also { response ->
                        response.replyToMessageId = callbackQuery.message.messageId
                    })
                } else if (callbackQuery.message.isReply && callbackQuery.message.replyToMessage.hasDocument()) {
                    execute(EditMessageText().also { response ->
                        response.text = "Datei wird gedruckt..."
                        response.messageId = callbackQuery.message.messageId
                        response.chatId = callbackQuery.message.chatId.toString()
                    })

                    printTelegramFile(user, callbackQuery.message.replyToMessage.document.fileId)
                    execute(SendMessage(callbackQuery.message.chatId, "Datei wurde gedruckt!").also { response ->
                        response.replyToMessageId = callbackQuery.message.replyToMessage.messageId
                    })
                }

            callbackQuery.data.startsWith("toggle|") -> {
                val resourceId = callbackQuery.data.drop(7).toLong()
                val iliasResources = IliasResourceStorage.get(user,
                        callbackQuery.message.chatId, callbackQuery.message.messageId)

                iliasResources.firstOrNull { it.entity?.key?.id == resourceId }?.let { resource ->
                    resource.selected = !resource.selected
                    IliasResourceStorage.updateSelected(resource)
                }

                execute(EditMessageReplyMarkup().also { response ->
                    response.messageId = callbackQuery.message.messageId
                    response.chatId = callbackQuery.message.chatId.toString()
                    response.replyMarkup = getIliasResourcesKeyboard(iliasResources)
                })
                execute(AnswerCallbackQuery().setCallbackQueryId(callbackQuery.id))
            }
        }

        // TODO: handle unknown or invalid stuff
    }


    private fun validateTelegramFile(document: Document) =
            document.fileName.endsWith(".pdf") && document.mimeType == "application/pdf"

    private fun printTelegramFile(user: Entity, fileId: String) {
        val file = execute(GetFile().also { it.fileId = fileId })
        RemoteHost.printTelegramFile(user, file)
    }

    private fun printIliasResources(callbackQuery: CallbackQuery, user: Entity,
                                    iliasResources: List<IliasResource>, text: String) {
        execute(EditMessageText().also { response ->
            response.messageId = callbackQuery.message.messageId
            response.text = text
            response.chatId = callbackQuery.message.chatId.toString()
            response.inlineMessageId = callbackQuery.inlineMessageId
        })

        RemoteHost.printIliasResources(user, iliasResources)
        IliasResourceStorage.delete(iliasResources)

        execute(SendMessage(callbackQuery.message.chatId, "Dateien wurden gedruckt!").also { response ->
            response.replyToMessageId = callbackQuery.message.messageId
        })
    }


    private fun getIliasResourcesKeyboard(iliasResources: List<IliasResource>): InlineKeyboardMarkup {
        val buttons = iliasResources.map {
            InlineKeyboardButton("${if (it.selected) "☒" else "☐"} ${it.name}")
                    .setCallbackData("toggle|${it.entity?.key?.id}")
        }.plus(InlineKeyboardButton("Drucken!").setCallbackData("printIliasFiles"))

        return InlineKeyboardMarkup().also { it.keyboard = buttons.chunked(3) }
    }

    override fun getBotToken() = BOT_TOKEN

    override fun getBotUsername() = "UniPrintBot"
}


class UniPrintBot : PollingUniPrintBot() {
    override fun clearWebhook() {
    }
}
