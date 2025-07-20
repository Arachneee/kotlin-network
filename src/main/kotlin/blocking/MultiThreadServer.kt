package blocking

import util.ThreadLogUtil.log
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() {
    val threadPool = Executors.newFixedThreadPool(2)
    val sessionManager = SessionManager()
    ServerSocket(9999).use { serverSocket ->
        log("멀티스레드 서버를 시작합니다...")

        Runtime.getRuntime().addShutdownHook(
            Thread {
                log("서버를 종료합니다...")
                serverSocket.close()
                sessionManager.shutdown()

                threadPool.shutdown()
                try {
                    if (!threadPool.awaitTermination(3, TimeUnit.SECONDS)) {
                        log("스레드풀이 정상 종료되지 않아 강제 종료합니다.")
                        threadPool.shutdownNow()
                    }
                } catch (e: InterruptedException) {
                    threadPool.shutdownNow()
                }

                log("서버가 종료되었습니다.")
            },
        )

        while (!Thread.currentThread().isInterrupted) {
            try {
                val clientSocket = serverSocket.accept()
                log("클라이언트 접속: ${clientSocket.inetAddress}")

                threadPool.execute {
                    sessionManager.addClient(clientSocket)
                }
            } catch (e: SocketException) {
                if (!serverSocket.isClosed) throw e
                break
            }
        }
    }
}
