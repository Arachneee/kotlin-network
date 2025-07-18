package blocking

import util.ThreadLogUtil.log
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

fun main() {
    log("클라이언트를 시작합니다...")

    /* 시스템 콜 흐름
     * socket() : 파일 디스크립터 생성 요청
     * connect() : 서버에 연결 요청 TCP 3-way 핸드쉐이크
     * close() : 소켓을 닫아 파일 디스크립터를 해제
     */
    Socket("127.0.0.1", 9999).use { socket ->
        log("서버에 연결되었습니다.")
        log("메시지를 입력하세요. 종료하려면 'exit'을 입력하세요.")

        val writer = socket.getOutputStream().bufferedWriter()
        val reader = socket.getInputStream().bufferedReader()

        val isRunning = AtomicBoolean(true)

        val receiveThread =
            thread(name = "ReceiveThread") {
                try {
                    while (isRunning.get()) {
                        val receivedMessage = reader.readLine()
                        if (receivedMessage == null) {
                            log("서버와의 연결이 끊어졌습니다.")
                            break
                        }
                        log("서버 : $receivedMessage")
                    }
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        log("메시지 수신 중 오류 발생: ${e.message}")
                    }
                }
            }

        try {
            while (isRunning.get()) {
                print("메시지 입력: ")
                val messageToSend = readlnOrNull() ?: ""

                if (messageToSend.equals("exit", ignoreCase = true)) {
                    log("클라이언트를 종료합니다.")
                    isRunning.set(false)
                    break
                }

                // send() : 사용자 공간에서 커널 공간으로 데이터를 전송
                writer.write(messageToSend)
                writer.newLine()
                writer.flush()
            }
        } catch (e: Exception) {
            log("메시지 송신 중 오류 발생: ${e.message}")
        } finally {
            isRunning.set(false)
            receiveThread.interrupt()
            receiveThread.join(1000) // 1초 대기
        }
    }
}
