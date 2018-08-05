import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier

fun main(args: Array<String>) {
    val defaultConfig = DefaultConfig()
    defaultConfig.keepAliveProvider = KeepAliveProvider.KEEP_ALIVE

    SSHClient(defaultConfig).use { sshClient ->
        sshClient.addHostKeyVerifier(PromiscuousVerifier())
        sshClient.connect(SSH_HOST)
        sshClient.authPassword(SSH_USER, SSH_PASSWORD)

        sshClient.newSFTPClient().use { sftpClient ->
            sftpClient.ls("Documents/print").forEach {
                println(it.name)
            }
        }

//        sshClient.startSession().use { session ->
//            val command = session.exec("ls")
//            command.inputStream.copyTo(System.out)
//            command.join(5, TimeUnit.SECONDS)
//            println(command.exitStatus)
//        }
    }
}
