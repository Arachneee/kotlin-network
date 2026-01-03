package server.blocking

import server.MessageHandler
import util.ThreadLogUtil.log
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BlockingServer(
    private val port: Int,
    private val messageHandler: MessageHandler,
) {
    private val threadPool: ExecutorService = Executors.newFixedThreadPool(10)
    private val sessionManager = SessionManager(messageHandler)

    fun start() {
        ServerSocket(port).use { serverSocket ->
            log("Blocking 서버가 포트 $port 에서 시작되었습니다.")

            Runtime.getRuntime().addShutdownHook(
                Thread {
                    gracefulShutdown(serverSocket)
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

    private fun gracefulShutdown(serverSocket: ServerSocket) {
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
    }
}
