package bot

import AUTH_USER
import BOT_TOKEN
import com.google.cloud.datastore.Entity
import kotlinx.coroutines.experimental.launch
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.methods.GetFile
import org.telegram.telegrambots.api.methods.send.SendChatAction
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import remote.getUser
import remote.printCommand
import remote.saveUpload
import remote.sshClient
import java.util.concurrent.TimeUnit

open class PollingUniPrintBot : TelegramLongPollingBot() {
    private var fileList: BotFileList? = null

    fun processUpdate(update: Update): BotApiMethod<*>? {
        if (update.hasMessage()) {
            val user = getUser(update.message.from.id)
                    ?: return SendMessage(update.message.chatId, "Permission Denied for user ${update.message.from.id}")
            return processMessage(update, user)
        } else if (update.hasCallbackQuery()) {
            val user = getUser(update.callbackQuery.from.id)
                    ?: return SendMessage(update.message.chatId, "Permission Denied for user ${update.message.from.id}")
            return processCallbackQuery(update.callbackQuery, user)
        }

        return null
    }

    private fun processMessage(update: Update, user: Entity): BotApiMethod<*>? {
        if (update.message.hasText()) {
            // process commands
            if (update.message.text == "/start") {
                if (update.message.from.id != AUTH_USER) {
                    return SendMessage(update.message.chatId, "Permission Denied for user ${update.message.from.id}")
                }

                launch {
                    fileList = SshBotFileList(false)
                    execute(SendMessage(update.message.chatId, "Welche Dateien sollen gedruckt werden?").also { message ->
                        message.replyMarkup = getFilesKeyboard()
                    })
                }
            } else if (update.message.text == "/statistik") {
                launch {
                    fileList = IliasBotFileList()
                    execute(SendMessage(update.message.chatId, "Welche Dateien sollen gedruckt werden?").also { message ->
                        message.replyMarkup = getFilesKeyboard()
                    })
                }
            } else {
                return SendMessage(update.message.chatId, "Ich habe dich leider nicht verstanden.")
            }

            return SendChatAction(update.message.chatId, "typing")

        } else if (update.message.hasDocument() && update.message.document.fileName.endsWith(".pdf")) {
            // process uploaded pdfs
            val keyboard = InlineKeyboardMarkup()
            keyboard.keyboard.add(listOf(InlineKeyboardButton("${update.message.document.fileName} drucken")
                    .setCallbackData("file|${update.message.document.fileId}")))
            saveUpload(user, update.message.document)

            return SendMessage(update.message.chatId, "Bestätige den Druckvorgang:").also { message ->
                message.replyToMessageId = update.message.messageId
                message.replyMarkup = keyboard
            }
        }

        return SendMessage(update.message.chatId, "Ich verarbeite nur PDF-Dateien.")
    }

    private fun processCallbackQuery(callbackQuery: CallbackQuery, user: Entity): BotApiMethod<*> {
        val split = callbackQuery.data.split("|")
        if (split[0] == "file") {
            launch {
                val file = execute(GetFile().also { it.fileId = split[1] })
                sshClient { client ->
                    client.startSession().use { session ->
                        session.exec("""temp_file=$(mktemp);
                            |wget -O ${"$"}temp_file "${file.getFileUrl(BOT_TOKEN)}";
                            |${printCommand("\$temp_file", user)}""".trimMargin()
                        ).join(30, TimeUnit.SECONDS)
                    }
                }

                execute(SendMessage(callbackQuery.message.chatId, "Datei wurde gedruckt!").also { message ->
                    message.replyToMessageId = callbackQuery.message.replyToMessage.messageId
                })
            }

            return EditMessageText().also { message ->
                message.text = "Datei wird gedruckt..."
                message.messageId = callbackQuery.message.messageId
                message.chatId = callbackQuery.message.chatId.toString()
            }
        }


        // process ssh file actions
        if (callbackQuery.from.id != AUTH_USER) {
            return SendMessage(callbackQuery.message.chatId, "Permission Denied for user ${callbackQuery.from.id}")
        }

        if (split[0] == "print" && fileList != null) {
            val message = EditMessageText()
            val selected = fileList!!.files.filter { it.selected }
            if (selected.isEmpty()) {
                return AnswerCallbackQuery().also { it.callbackQueryId = callbackQuery.id }
            } else if (selected.size == 1) {
                message.text = "${selected.single().name} wird gedruckt..."
            } else {
                val first = selected.dropLast(1).joinToString(", ", transform = BotFile::name)
                message.text = "$first und ${selected.last().name} werden gedruckt..."
            }

            launch {
                fileList!!.printSelected(user)
                execute(SendMessage(callbackQuery.message.chatId, "Dateien wurde gedruckt!").also {
                    it.replyToMessageId = callbackQuery.message.messageId
                })
            }

            message.messageId = callbackQuery.message.messageId
            message.chatId = callbackQuery.message.chatId.toString()
            message.inlineMessageId = callbackQuery.inlineMessageId
            return message
        } else if (split[0] == "archive" || split[0] == "main") {
            fileList = SshBotFileList(if (fileList is SshBotFileList) !(fileList as SshBotFileList).useArchive else false)
        } else if (split[0] == "toggleFile" && split.size == 3) {
            fileList?.files?.firstOrNull { it.name == split[1] }?.let {
                it.selected = !it.selected
            }
        }

        return EditMessageReplyMarkup().also { message ->
            message.messageId = callbackQuery.message.messageId
            message.chatId = callbackQuery.message.chatId.toString()
            message.replyMarkup = getFilesKeyboard()
        }
    }

    private fun getFilesKeyboard(): InlineKeyboardMarkup {
        val buttons = fileList!!.files.map {
            InlineKeyboardButton("${if (it.selected) "☒" else "☐"} ${it.name}")
                    .setCallbackData("toggleFile|${it.name}|${it.selected}")
        }.toMutableList()

        if (fileList is SshBotFileList) {
            if ((fileList as SshBotFileList).useArchive) {
                buttons.add(InlineKeyboardButton("Aktiv").setCallbackData("main"))
            } else {
                buttons.add(InlineKeyboardButton("Archiv").setCallbackData("archive"))
            }
        }
        buttons.add(InlineKeyboardButton("Drucken!").setCallbackData("print"))

        return InlineKeyboardMarkup().also { it.keyboard = buttons.chunked(3) }
    }


    override fun onUpdateReceived(update: Update) {
        processUpdate(update)?.let {
            when (it) {
                is SendMessage -> execute(it)
                is SendChatAction -> execute(it)
                is EditMessageText -> execute(it)
                is EditMessageReplyMarkup -> execute(it)
                else -> TODO("not implemented")
            }
        }
    }

    override fun getBotToken() = BOT_TOKEN

    override fun getBotUsername() = "UniPrintBot"
}


class UniPrintBot : PollingUniPrintBot() {
    override fun clearWebhook() {
    }
}
