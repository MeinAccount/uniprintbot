package heiko

import HEIKO_GROUP
import HEIKO_TODAY
import com.google.cloud.datastore.Entity
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.UnpinAllChatMessages
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.UnpinChatMessage
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendSticker
import org.telegram.telegrambots.meta.api.objects.InputFile
import remote.datastore
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.*
import javax.servlet.annotation.HttpConstraint
import javax.servlet.annotation.ServletSecurity
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet("/heiko/daily")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class HeikoDailyController : HttpServlet() {
    private val bot = lazy { HeikoNotificationBot() }
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        retrieveSDates().forEach {
            if (extractLocalDate(it) == LocalDate.now()) {
                val chatId = it.getLong("chatId")

                bot.value.execute(SendMessage(chatId.toString(), HEIKO_TODAY).apply {
                    parseMode = "Markdown"
                })
                bot.value.execute(SendSticker(chatId.toString(), InputFile(positiveStickers.random())))
            }
        }
    }
}

@WebServlet("/heiko/today")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class HeikoTodayController : HttpServlet() {
    override fun doGet(req: HttpServletRequest?, resp: HttpServletResponse?) {
        HeikoNotificationBot().apply {
            execute(SendMessage(HEIKO_GROUP, HEIKO_TODAY).apply {
                parseMode = "Markdown"
            })
            execute(SendSticker(HEIKO_GROUP, InputFile(positiveStickers.random())))
        }
    }
}

@WebServlet("/heiko/poll")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class HeikoPollController : HttpServlet() {
    private val formatter = DateTimeFormatter.ofPattern("EEEE dd.MM.y", Locale.GERMANY)
    override fun doGet(req: HttpServletRequest?, resp: HttpServletResponse?) {
        HeikoNotificationBot().apply {
            // first unpin all messages
            execute(UnpinAllChatMessages(HEIKO_GROUP))

            // compute week
            val weekStart = LocalDate.now(ZoneId.of("Europe/Berlin")).run {
                when (dayOfWeek) {
                    DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    else -> this
                }
            }

            val calendarWeek = WeekFields.of(Locale.GERMANY).weekOfWeekBasedYear()
            val pollMessage = execute(SendPoll(
                HEIKO_GROUP, "Vor-Ort Spieleabend KW${weekStart.get(calendarWeek)}", sequence {
                    var day = weekStart
                    do {
                        yield(day.format(formatter))
                        day = day.plusDays(1)
                    } while (day.dayOfWeek != DayOfWeek.MONDAY)
                    yield("Diese Woche leider nicht")
                    yield("Ich zähle als geimpft / genesen")
                }.toList()
            ).apply {
                isAnonymous = false
                allowMultipleAnswers = true
            })
            execute(PinChatMessage(pollMessage.chatId.toString(), pollMessage.messageId))
        }
    }
}


@WebServlet("/heiko/cleanup")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class HeikoCleanupController : HttpServlet() {
    private val bot = lazy { HeikoNotificationBot() }
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        retrieveSDates().forEach {
            if (extractLocalDate(it) < LocalDate.now()) {
                val chatId = it.getLong("chatId")
                datastore.delete(it.key)

                val chat = bot.value.execute(GetChat(chatId.toString()))
                if (chat.pinnedMessage.messageId == it.getInt("messageId") + 1) {
                    bot.value.execute(UnpinChatMessage(chatId.toString()))
                }
            }
        }
    }
}


fun extractLocalDate(entity: Entity): LocalDate =
    LocalDate.of(entity.getInt("year"), entity.getInt("month"), entity.getInt("day"))

private fun Entity.getInt(name: String) = getLong(name).toInt()
