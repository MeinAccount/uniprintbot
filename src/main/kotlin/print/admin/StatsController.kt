package print.admin

import com.google.cloud.Timestamp
import com.google.cloud.datastore.Query
import com.google.cloud.datastore.StructuredQuery
import com.google.cloud.datastore.StructuredQuery.CompositeFilter.and
import com.google.cloud.datastore.StructuredQuery.PropertyFilter.ge
import com.google.cloud.datastore.StructuredQuery.PropertyFilter.lt
import kotlinx.html.*
import remote.UserStorage
import remote.datastore
import java.text.DecimalFormat
import java.util.*
import javax.servlet.annotation.HttpConstraint
import javax.servlet.annotation.ServletSecurity
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.math.log10
import kotlin.math.pow


@WebServlet("/stats")
@ServletSecurity(
    HttpConstraint(
        rolesAllowed = arrayOf("admin"),
        transportGuarantee = ServletSecurity.TransportGuarantee.CONFIDENTIAL
    )
)
open class StatsController : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val users = UserStorage.listUsers().asSequence().map {
            it.key.name to UserStat(it.key.name, it.getString("name"))
        }.toMap()

        val queryBuilder = Query.newEntityQueryBuilder()
            .setKind("PrintJob")
            .setOrderBy(StructuredQuery.OrderBy.desc("time"))
        val yearFilter = req.getParameter("year")?.toIntOrNull()?.also {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"))
            calendar.set(it, 0, 1)
            val start = Timestamp.of(calendar.time)
            calendar.set(it + 1, 0, 1)
            queryBuilder.setFilter(and(ge("time", start), lt("time", Timestamp.of(calendar.time))))
        }
        val yearSuffix = if (yearFilter == null) "" else "?year=$yearFilter"

        val jobs = datastore.run(queryBuilder.build()).asSequence().map {
            val user = users[it.key.parent.name]!!
            JobStat(
                user, it.getString("fileId"), it.getString("fileName"),
                it.getLong("fileSize"), it.getTimestamp("time")
            ).also { job ->
                user.totalSize += job.size
                user.jobs.add(job)
            }
        }.toList()


        htmlTemplate(resp, "@UniPrintBot Statistik") {
            header(yearFilter, yearSuffix)
            div("center") {
                div { id = "chart" }
                table {
                    users.values.filter { it.jobs.isNotEmpty() }
                        .sortedWith(compareByDescending<UserStat> { it.jobs.size }
                            .thenByDescending { it.totalSize }).map { user ->
                            tr {
                                td { a("/stats/${user.userId}$yearSuffix") { text(user.name) } }
                                td { text("${user.jobs.size} Aufträge") }
                                td("right") { text("(${String.format("%.2f", user.jobs.size * 100.0 / jobs.size)}%)") }
                                td("right") { text(formatBytes(user.totalSize)) }
                            }
                        }
                }
            }

            content(req, users, jobs, yearSuffix)

            script(src = "https://cdn.jsdelivr.net/npm/apexcharts") { }
            script {
                unsafe {
                    val (labels, series) = users.values.filter { it.jobs.size > 0 }
                        .sortedBy { it.name }
                        .fold("" to "") { (labels, series), user ->
                            "$labels, \"${user.name}\"" to "$series, ${user.jobs.size}"
                        }

                    //language=JavaScript
                    raw(
                        """
new ApexCharts(document.querySelector('#chart'), {
    chart: {
        width: 400,
        type: 'pie',
    },
    labels: [MY_LABELS],
    series: [MY_VALUES]
}).render();""".replace("MY_LABELS", labels.drop(2)).replace("MY_VALUES", series.drop(2))
                    )
                }
            }
        }
    }

    internal open fun FlowContent.content(
        req: HttpServletRequest,
        users: Map<String, UserStat>,
        jobs: List<JobStat>,
        yearSuffix: String
    ) {
        h1 {
            a("/stats$yearSuffix") { text("Letzte Druckaufträge") }
        }
        listJobs(jobs, yearSuffix)
    }

    internal fun FlowContent.listJobs(jobs: Iterable<JobStat>, yearSuffix: String) {
        div("center") {
            table {
                jobs.map { job ->
                    tr {
                        td { a("/stats/${job.user.userId}$yearSuffix") { text(job.user.name) } }
                        td { printTime(job.time) }
                        td { a("/download/${job.fileId}") { text(job.name) } }
                        td("right") { text(job.readableSize()) }
                    }
                }
            }
        }
    }
}


internal data class UserStat(
    val userId: String, val name: String,
    var totalSize: Long = 0, val jobs: MutableList<JobStat> = LinkedList()
)

internal data class JobStat(
    val user: UserStat,
    val fileId: String,
    val name: String,
    val size: Long,
    val time: Timestamp
) {
    fun readableSize() = formatBytes(size)
}


private val units = arrayOf("B", "kB", "MB", "GB", "TB")
private fun formatBytes(size: Long): String {
    if (size <= 0) return "0"
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
}
