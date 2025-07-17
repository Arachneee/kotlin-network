package blocking

import util.ThreadLogUtil.log
import java.net.ServerSocket
import kotlin.concurrent.thread // 코틀린의 스레드 생성 함수 import

fun main() {
    val serverSocket = ServerSocket(9999)
    log("멀티스레드 에코 서버를 시작합니다...")

    while (true) {
        val clientSocket = serverSocket.accept()
        log("클라이언트 접속: ${clientSocket.inetAddress}")

        thread {
            try {
                connectClient(clientSocket)
            } catch (e: Exception) {
                log("클라이언트(${clientSocket.inetAddress})와 연결 중 예외 발생: ${e.message}")
            } finally {
                log("클라이언트(${clientSocket.inetAddress}) 연결 종료")
            }
        }
    }
}
