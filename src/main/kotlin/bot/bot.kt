package bot

import AUTH_USER
import BOT_TOKEN
import com.google.cloud.datastore.Entity
import kotlinx.coroutines.experimental.launch
import org.telegram.telegrambots.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.api.methods.BotApiMethod
import org.telegram.telegrambots.api.methods.GetFile
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.api.objects.CallbackQuery
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import java.io.File
import java.util.concurrent.TimeUnit

data class AvailableFile(val path: String, val name: String, var selected: Boolean, val inArchive: Boolean)

open class PollingUniPrintBot : TelegramLongPollingBot() {
    private var useArchive = false
    private var files = emptyList<AvailableFile>()

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

    private fun processMessage(update: Update, user: Entity): SendMessage? {
        if (update.message.hasText() && update.message.from.id == AUTH_USER) {
            if (update.message.text == "/start") {
                useArchive = false
                loadRemoteFiles()

                val message = SendMessage(update.message.chatId, "Welche Dateien sollen gedruckt werden?")
                message.replyMarkup = getFilesKeyboard()
                return message
            }

            return SendMessage(update.message.chatId, "Ich habe dich leider nicht verstanden.")
        } else if (update.message.hasDocument() && update.message.document.fileName.endsWith(".pdf")) {
            val keyboard = InlineKeyboardMarkup()
            keyboard.keyboard.add(listOf(InlineKeyboardButton("${update.message.document.fileName} drucken")
                    .setCallbackData("file|${update.message.document.fileId}")))

            val message = SendMessage(update.message.chatId, "Bestätige den Druckvorgang:")
            message.replyToMessageId = update.message.messageId
            message.replyMarkup = keyboard

            return message
        }

        return SendMessage(update.message.chatId, "Ich verarbeite nur PDF-Dateien.")
    }

    private fun processCallbackQuery(callbackQuery: CallbackQuery, user: Entity): BotApiMethod<*> {
        val split = callbackQuery.data.split("|")
        if (split[0] == "file") {
            launch {
                val fileRetrieval = GetFile()
                fileRetrieval.fileId = split[1]

                val result = execute(fileRetrieval)
                sshClient { client ->
                    client.startSession().use { session ->
                        session.exec("temp_file=\$(mktemp); " +
                                "wget -O \$temp_file \"${result.getFileUrl(BOT_TOKEN)}\"; " +
                                printCommand("\$temp_file", user)).join(30, TimeUnit.SECONDS)
                    }
                }

                val messageDone = SendMessage(callbackQuery.message.chatId, "Datei wurde gedruckt!")
                messageDone.replyToMessageId = callbackQuery.message.replyToMessage.messageId
                execute(messageDone)
            }

            val message = EditMessageText()
            message.text = "Datei wird gedruckt..."
            message.messageId = callbackQuery.message.messageId
            message.chatId = callbackQuery.message.chatId.toString()
            return message
        }


        // process ssh file actions
        if (callbackQuery.from.id != AUTH_USER) {
            return SendMessage(callbackQuery.message.chatId, "Permission Denied for user ${callbackQuery.message.from.id}")
        }

        if (split[0] == "print") {
            val message = EditMessageText()
            val selected = files.filter { it.selected }
            if (selected.isEmpty()) {
                return AnswerCallbackQuery().also {
                    it.callbackQueryId = callbackQuery.id
                }
            } else if (selected.size == 1) {
                message.text = "${selected.single().name} wird gedruckt..."
            } else {
                val first = selected.dropLast(1).joinToString(", ", transform = AvailableFile::name)
                message.text = "$first und ${selected.last().name} werden gedruckt..."
            }

            launch {
                printSelectedFiles()

                val messageDone = SendMessage(callbackQuery.message.chatId, "Dateien wurde gedruckt!")
                messageDone.replyToMessageId = callbackQuery.message.messageId
                execute(messageDone)
            }

            message.messageId = callbackQuery.message.messageId
            message.chatId = callbackQuery.message.chatId.toString()
            message.inlineMessageId = callbackQuery.inlineMessageId
            return message
        } else if (split[0] == "archive" || split[0] == "main") {
            useArchive = !useArchive
            loadRemoteFiles()
        } else if (split[0] == "toggleFile" && split.size == 3) {
            files.firstOrNull { it.path == split[1] }?.let {
                it.selected = !it.selected
            }
        }

        // update keyboard
        val message = EditMessageReplyMarkup()
        message.messageId = callbackQuery.message.messageId
        message.chatId = callbackQuery.message.chatId.toString()
        message.replyMarkup = getFilesKeyboard()
        return message
    }


    private fun printSelectedFiles() {
        sshClient { client ->
            client.startSession().use { session ->
                session.exec(files.filter { it.selected }.joinToString("; ") {
                    return@joinToString if (it.inArchive) {
                        printCommand(it.path)
                    } else {
                        val file = File(it.path)
                        val target = "${file.parentFile}/archive/${file.name}"

                        "mv \"${it.path}\" \"$target\"; ${printCommand(target)}"
                    }
                }).join(5, TimeUnit.SECONDS)
            }
        }
    }

    private fun loadRemoteFiles() {
        files = sshClient { client ->
            client.newSFTPClient().use { sftpClient ->
                sftpClient.ls(if (useArchive) "Documents/print/archive" else "Documents/print")
            }
        }.filter { it.isRegularFile }.map { file ->
            AvailableFile(file.path, file.name.substringBeforeLast("."), !useArchive, useArchive)
        }
    }

    private fun getFilesKeyboard(): InlineKeyboardMarkup {
        val markup = InlineKeyboardMarkup()
        markup.keyboard = files.map {
            InlineKeyboardButton("${if (it.selected) "☒" else "☐"} ${it.name}")
                    .setCallbackData("toggleFile|${it.path}|${it.selected}")
        }.plus(if (useArchive) InlineKeyboardButton("Aktiv").setCallbackData("main") else
            InlineKeyboardButton("Archiv").setCallbackData("archive")
        ).plus(InlineKeyboardButton("Drucken!").setCallbackData("print")).chunked(3)

        return markup
    }


    override fun onUpdateReceived(update: Update) {
        processUpdate(update)?.let {
            when (it) {
                is SendMessage -> execute(it)
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
