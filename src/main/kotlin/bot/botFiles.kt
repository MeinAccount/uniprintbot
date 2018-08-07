package bot

import com.google.cloud.datastore.Entity
import net.schmizz.sshj.xfer.InMemorySourceFile
import remote.downloadFile
import remote.getIliasFiles
import remote.printCommand
import remote.sshClient
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

abstract class BotFile(open val name: String, open var selected: Boolean)
abstract class BotFileList(open val files: List<BotFile>) {
    abstract fun printSelected(user: Entity)
}


data class SshBotFileList(override val files: List<SshBotFile>, val useArchive: Boolean) : BotFileList(files) {
    constructor (useArchive: Boolean) : this(sshClient { client ->
        client.newSFTPClient().use { sftpClient ->
            sftpClient.ls(if (useArchive) "Documents/print/archive" else "Documents/print")
        }
    }.filter { it.isRegularFile }.map { file ->
        SshBotFile(file.name.substringBeforeLast("."), !useArchive, file.path, useArchive)
    }, useArchive)


    override fun printSelected(user: Entity) {
        sshClient { client ->
            client.startSession().use { session ->
                session.exec(files.filter { it.selected }.joinToString("; ") {
                    return@joinToString if (it.inArchive) {
                        printCommand(it.path, user)
                    } else {
                        val file = File(it.path)
                        val target = "${file.parentFile}/archive/${file.name}"

                        "mv \"${it.path}\" \"$target\"; ${printCommand(target, user)}"
                    }
                }).join(5, TimeUnit.SECONDS)
            }
        }
    }

    data class SshBotFile(override val name: String, override var selected: Boolean,
                          val path: String, val inArchive: Boolean) : BotFile(name, selected)
}


data class IliasBotFileList(override val files: List<IliasBotFile>) : BotFileList(files) {
    constructor() : this(getIliasFiles().map { (name, url) ->
        IliasBotFile(name, false, url)
    })


    override fun printSelected(user: Entity) {
        sshClient { client ->
            val tempDir = client.startSession().use { session ->
                val command = session.exec("mktemp -d")
                val tempDir = command.inputStream.bufferedReader().use { it.readText() }
                command.join(5, TimeUnit.SECONDS)
                return@use tempDir
            }

            val upload = client.newSCPFileTransfer().newSCPUploadClient()
            val command = files.filter { it.selected }.mapIndexedNotNull { index, file ->
                val data = downloadFile(file.url) ?: return@mapIndexedNotNull null

                val filename = "${tempDir.trim()}/$index"
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
                }, filename)
                return@mapIndexedNotNull filename
            }.joinToString("; ") { printCommand(it, user) }

            client.startSession().use { session ->
                session.exec(command).join(5, TimeUnit.SECONDS)
            }
        }
    }

    data class IliasBotFile(override val name: String, override var selected: Boolean,
                            val url: String) : BotFile(name, selected)
}
