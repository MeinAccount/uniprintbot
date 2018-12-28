package print

import com.google.cloud.Timestamp
import com.google.cloud.datastore.Query
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import remote.UserStorage
import remote.datastore
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import javax.servlet.annotation.HttpConstraint
import javax.servlet.annotation.ServletSecurity
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


@WebServlet("/stats")
@ServletSecurity(HttpConstraint(rolesAllowed = arrayOf("admin")))
class StatsController : HttpServlet() {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss.SSS")

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val users = UserStorage.listUsers().asSequence().map {
            it.key.name to UserStat(it.key.name, it.getString("name"))
        }.toMap()

        val jobs = TreeSet<JobStat>(compareByDescending { it.time })
        datastore.run(Query.newEntityQueryBuilder().setKind("PrintJob").build()).forEach {
            val user = users[it.key.parent.name]!!
            val job = JobStat(user, it.getString("fileId"), it.getString("fileName"),
                    it.getLong("fileSize"), it.getTimestamp("time"))

            jobs.add(job)
            user.totalSize += job.size
            user.jobs.add(job)
        }


        resp.writer.println("<!DOCTYPE html>")
        resp.writer.appendHTML().html {
            lang = "de"
            head {
                title("@UniPrintBot stats")
                styleLink("assets/stats.css")
            }

            body {
                h1 { text("@UniPrintBot Statistik") }
                div("center") {
                    div { id = "chart" }

                    table {
                        users.values.sortedByDescending { it.jobs.size }.map { user ->
                            tr {
                                td { text(user.name) }
                                td { text("${user.jobs.size} Aufträge") }
                                td("right") { text("(${String.format("%.2f", user.jobs.size * 100.0 / jobs.size)}%)") }
                                td("right") { text(formatBytes(user.totalSize)) }
                            }
                        }
                    }
                }

                h1 {
                    a("stats") { text("Letzte Druckaufträge") }
                    req.getParameter("user")?.let {
                        text(" von ${users[it]?.name ?: "unbekannt"}")
                    }
                }
                div("center") {
                    table {
                        (req.getParameter("user")?.let { userIdFilter ->
                            jobs.filter { it.user.userId == userIdFilter }
                        } ?: jobs).map { job ->
                            tr {
                                td { a("stats?user=${job.user.userId}") { text(job.user.name) } }
                                td {
                                    val utcLocalTime = LocalDateTime.ofEpochSecond(job.time.seconds, job.time.nanos, ZoneOffset.UTC)
                                    val offset = ZoneId.of("Europe/Berlin").rules.getOffset(Instant.now())

                                    // format the instant respecting the *current* (as in: not the daylight saving time
                                    // in use at the instant) daylight saving
                                    text(dateFormatter.format(utcLocalTime.plusSeconds(offset.totalSeconds.toLong())))
                                }
                                td { a("download/${job.fileId}") { text(job.name) } }
                                td("right") { text(job.readableSize()) }
                            }
                        }
                    }
                }

                script(src = "https://cdn.jsdelivr.net/npm/apexcharts") { }
                script {
                    unsafe {
                        val (labels, series) = users.values.filter { it.jobs.size > 0 }
                                .sortedBy { it.name }
                                .fold("" to "") { (labels, series), user ->
                                    "$labels, \"${user.name}\"" to "$series, ${user.jobs.size}"
                                }

                        //language=JavaScript
                        raw("""
new ApexCharts(document.querySelector('#chart'), {
    chart: {
        width: 400,
        type: 'pie',
    },
    labels: [MY_LABELS],
    series: [MY_VALUES]
}).render();""".replace("MY_LABELS", labels.drop(2)).replace("MY_VALUES", series.drop(2)))
                    }
                }
            }
        }
    }
}


data class UserStat(val userId: String, val name: String,
                    var totalSize: Long = 0, val jobs: MutableList<JobStat> = LinkedList())


data class JobStat(val user: UserStat, val fileId: String, val name: String, val size: Long, val time: Timestamp) {
    fun readableSize() = formatBytes(size)
}

private val units = arrayOf("B", "kB", "MB", "GB", "TB")
private fun formatBytes(size: Long): String {
    if (size <= 0) return "0"
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
