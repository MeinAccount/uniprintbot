package heiko

import HEIKO_FORCE_USER
import HEIKO_GROUP
import HEIKO_TOKEN
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.language.v1.Document
import com.google.cloud.language.v1.LanguageServiceClient
import com.google.cloud.language.v1.LanguageServiceSettings
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendSticker
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.File
import java.io.FileInputStream
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Year
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.regex.Pattern

private val client = LanguageServiceClient.create(LanguageServiceSettings.newBuilder()
        .setCredentialsProvider(FixedCredentialsProvider.create(GoogleCredentials.fromStream(
                if (File("WEB-INF/uniprintbot.json").exists())
                    FileInputStream("WEB-INF/uniprintbot.json")
                else FileInputStream("src/main/webapp/WEB-INF/uniprintbot.json")))).build())

const val DATE_REGEX = ".*(?:([0-9]{4})-)?([0-9]{2})-([0-9]{2}).*"
private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d. MMMM YYYY", Locale.GERMAN)

class HeikoNotificationBot : TelegramLongPollingBot() {
    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage()) {
            if (update.message.hasText()) {
                processMessage(update.message, update.message.from.id == HEIKO_FORCE_USER)
            } else if (update.message.hasSticker() && update.message.chatId == -1001374318263L) {
                execute(SendMessage(update.message.chatId, "Sticker fileId ${update.message.sticker.fileId}")
                        .setReplyToMessageId(update.message.messageId))
            }
        } else if (update.hasPollAnswer()) {
            // Bots receive new votes only in polls that were sent by the bot itself
            if (update.pollAnswer.optionIds.size > 1 && update.pollAnswer.optionIds.contains(7)) {
                val name = listOfNotNull(update.pollAnswer.user.firstName, update.pollAnswer.user.lastName)
                        .joinToString(" ")
                        .ifEmpty { update.pollAnswer.user.userName }
                execute(SendMessage(HEIKO_GROUP, "Was soll das [$name](tg://user?id=${update.pollAnswer.user.id})? Ich bin verwirrt\\.\\.\\.")
                        .setParseMode("MarkdownV2"))
            }
        }
    }

    private fun processMessage(message: Message, force: Boolean = false, score: Boolean = false) {
        when {
            message.text.startsWith("/next", true) -> {
                val matcher = Pattern.compile(DATE_REGEX).matcher(message.text)
                if (matcher.matches()) {
                    try {
                        val date = LocalDate.of(matcher.group(1)?.toInt() ?: Year.now().value,
                                matcher.group(2).toInt(), matcher.group(3).toInt())
                        if (date >= LocalDate.now()) {
                            val pinned = execute(SendMessage(message.chatId,
                                    "Nächster Spieleabend am ${date.format(DATE_FORMAT)}!"))
                            execute(PinChatMessage(pinned.chatId, pinned.messageId))
                            storeSDate(date, message)
                        }
                    } catch (e: DateTimeException) {
                    } catch (e: TelegramApiException) {
                        e.printStackTrace()
                    }
                }
            }

            message.text.startsWith("/tag@HeikoNotificationBot", true) ->
                processStripped(message, message.text.drop(25), score)
            message.text.startsWith("/tag", true) ->
                processStripped(message, message.text.drop(4), score)

            message.text.startsWith("/score@HeikoNotificationBot", true) ->
                processStripped(message, message.text.drop(27), true)
            message.text.startsWith("/score", true) ->
                processStripped(message, message.text.drop(6), true)

            force || message.text.contains("@HeikoNotificationBot") ->
                processStripped(message, message.text, score)
        }
    }

    private fun processStripped(message: Message, text: String, score: Boolean) {
        if (text.isNotBlank()) {
            val sentiment = client.analyzeSentiment(Document.newBuilder()
                    .setContent(text).setType(Document.Type.PLAIN_TEXT).build())
            println(sentiment)

            if (score) {
                execute(SendMessage(message.chatId,
                        "Score ${sentiment.documentSentiment.score} at magnitude ${sentiment.documentSentiment.magnitude}")
                        .setReplyToMessageId(message.messageId))
            } else {
                execute(SendSticker()
                        .setChatId(message.chatId)
                        .setSticker(when {
                            sentiment.documentSentiment.score < 0 -> negativeStickers.random()
                            sentiment.documentSentiment.score > 0 -> positiveStickers.random()
                            else -> unknownStickers.random()
                        })
                        .setReplyToMessageId(message.messageId))
            }
        } else if (message.isReply) {
            processMessage(message.replyToMessage, true, score)
        }
    }


    override fun getBotUsername() = "HeikoNotificationBot"

    override fun getBotToken() = HEIKO_TOKEN
}
