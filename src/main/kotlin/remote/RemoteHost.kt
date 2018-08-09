package remote

import BOT_TOKEN
import SSH_HOST
import SSH_PASSWORD
import SSH_USER
import com.google.cloud.datastore.Entity
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.xfer.InMemorySourceFile
import org.telegram.telegrambots.api.objects.File
import java.io.InputStream
import java.util.*
import java.util.concurrent.TimeUnit

object RemoteHost {
    private fun <T> sshClient(run: (SSHClient) -> T) = SSHClient().use { sshClient ->
        sshClient.addHostKeyVerifier(PromiscuousVerifier())
        sshClient.connect(SSH_HOST)
        sshClient.authPassword(SSH_USER, SSH_PASSWORD)
        run(sshClient)
    }

    private fun printCommand(path: String, user: Entity? = null) =
            "echo \"$path von ${user?.getString("name")} am ${Date()}\" >> log"


    fun printTelegramFile(user: Entity, file: File) {
        sshClient { client ->
            client.startSession().use { session ->
                session.exec("""temp_file=$(mktemp);
                                |wget -O ${"$"}temp_file "${file.getFileUrl(BOT_TOKEN)}";
                                |${printCommand("\$temp_file", user)}""".trimMargin()
                ).join(30, TimeUnit.SECONDS)
            }
        }
    }

    fun printRemoteFiles(user: Entity, remoteFiles: List<RemoteFile>) {
        sshClient { client ->
            val tempDir = client.startSession().use { session ->
                val command = session.exec("mktemp -d")
                val tempDir = command.inputStream.bufferedReader().use { it.readText() }
                command.join(5, TimeUnit.SECONDS)
                return@use tempDir
            }

            val upload = client.newSCPFileTransfer().newSCPUploadClient()
            val command = remoteFiles.filter { it.selected }.mapIndexed { index, file ->
                val data = Ilias.download(file.url)
                val tempFilename = "${tempDir.trim()}/$index"
                upload.copy(object : InMemorySourceFile() {
                    override fun getLength(): Long {
                        return data.size.toLong()
                    }

                    override fun getName(): String {
                        return file.name
                    }

                    override fun getInputStream(): InputStream {
                        return data.inputStream()
                    }
                }, tempFilename)
                return@mapIndexed tempFilename
            }.joinToString("; ") { printCommand(it, user) }

            client.startSession().use { session ->
                session.exec(command).join(5, TimeUnit.SECONDS)
            }
        }
    }
}
