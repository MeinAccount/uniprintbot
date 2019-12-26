package print.admin

import com.google.cloud.Timestamp
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.servlet.http.HttpServletResponse

fun htmlTemplate(resp: HttpServletResponse, title: String, content: BODY.() -> Unit) {
    resp.contentType = "Content-Type: text/html; charset=UTF-8";
    resp.writer.println("<!DOCTYPE html>")
    resp.writer.appendHTML().html {
        lang = "de"
        head {
            meta(charset = "utf-8")
            title(title)
            styleLink("/assets/stats.css")
        }

        body { content() }
    }
}

fun FlowOrHeadingContent.header() {
    h1 {
        text("@UniPrintBot ")
        a("/stats") { text("Statistik") }
        text(" ")
        a("/management") { text("Verwaltung") }
    }
}

fun FORM.hiddenInput(name: String, value: String) {
    hiddenInput(name = name) { this.value = value }
}


private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss.SSS")
fun FlowOrPhrasingContent.printTime(time: Timestamp) {
    val instant = time.toDate().toInstant()
    val offset = ZoneId.of("Europe/Berlin").rules.getOffset(Instant.now())

    // format the instant respecting the *current* (as in: not the daylight saving time
    // in use at the instant) daylight saving
    time {
        dateTime = DateTimeFormatter.ISO_INSTANT.format(instant)
        text(dateFormatter.format(instant.atOffset(offset)))
    }
}
