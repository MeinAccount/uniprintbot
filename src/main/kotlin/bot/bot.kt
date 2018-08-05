package bot

import BOT_TOKEN
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
            if (update.message.from.id != 639133737) {
                return SendMessage(update.message.chatId, "Permission Denied for user ${update.message.from.id}")
            }

            return processMessage(update)
        } else if (update.hasCallbackQuery() && update.callbackQuery.from.id == 639133737) {
            return processCallbackQuery(update.callbackQuery)
        }

        return null
    }

    private fun processMessage(update: Update): SendMessage? {
        if (update.message.hasText()) {
            if (update.message.text == "/start") {
                useArchive = false
                loadRemoteFiles()

                val message = SendMessage(update.message.chatId, "Welche Dateien sollen gedruckt werden?")
                message.replyMarkup = getFilesKeyboard()
                return message
            }

            return SendMessage(update.message.chatId, "Ich habe dich leider nicht verstanden.")
        } else if (update.message.hasDocument() && update.message.document.fileName.endsWith(".pdf")) {
            launch {
                val fileRetrieval = GetFile()
                fileRetrieval.fileId = update.message.document.fileId

                val result = execute(fileRetrieval)
                sshClient { client ->
                    client.startSession().use { session ->
                        session.exec("temp_file=\$(mktemp); " +
                                "wget -O \$temp_file \"${result.getFileUrl(BOT_TOKEN)}\"; " +
                                "echo \$temp_file >> log").join(30, TimeUnit.SECONDS)
                    }
                }

                execute(SendMessage(update.message.chatId,
                        "${update.message.document.fileName} wurde gedruckt!"))
            }

            return null
        }

        return SendMessage(update.message.chatId, "Ich verarbeite nur PDF-Dateien.")
    }

    private fun processCallbackQuery(callbackQuery: CallbackQuery): BotApiMethod<*> {
        val split = callbackQuery.data.split("|")
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
                execute(SendMessage(callbackQuery.message.chatId, "Dateien wurde gedruckt!"))
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
        message.inlineMessageId = callbackQuery.inlineMessageId
        message.replyMarkup = getFilesKeyboard()
        return message
    }


    private fun printSelectedFiles() {
        sshClient { client ->
            client.startSession().use { session ->
                session.exec(files.filter { it.selected }.joinToString("; ") {
                    return@joinToString if (it.inArchive) {
                        "echo \"${it.path}\" >> log"
                    } else {
                        val file = File(it.path)
                        val target = "${file.parentFile}/archive/${file.name}"

                        "mv \"${it.path}\" \"$target\"; echo \"$target\" >> log"
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
