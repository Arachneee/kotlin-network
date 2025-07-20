package server.blocking

import util.ThreadLogUtil.log
import java.net.ServerSocket
import java.net.Socket

fun main() {
    log("TCP 서버를 시작합니다...")

    /* 시스템 콜 흐름
     * socket() : 파일 디스크립터 생성 요청
     * bind() : 소켓을 특정 주소와 포트에 바인딩
     * listen() : 클라이언트의 연결 요청을 받을 수 있는 대기 상태로 변경
     * close() : 소켓을 닫아 파일 디스크립터를 해제
     */
    ServerSocket(9999).use { serverSocket ->
        log("클라이언트의 연결을 기다립니다...")

        // Blocking I/O 방식으로 클라이언트의 연결을 기다림
        // accept() : 대기 상태에 있다가 클라이언트가 연결되면 새 소켓(파일 디스크립터)을 반환
        val clientSocket = serverSocket.accept()
        log("클라이언트 접속: ${clientSocket.inetAddress}")

        connectClient(clientSocket)
    }
    log("서버를 종료합니다.")
}

fun connectClient(clientSocket: Socket) {
    clientSocket.use { socket ->
        val reader = socket.inputStream.bufferedReader()
        val writer = socket.getOutputStream().bufferedWriter()

        log("클라이언트와 연결되었습니다. 메시지를 기다리는 중...")

        while (true) {
            try {
                // recv() : 커널 공간에서 데이터를 읽어 사용자 공간으로 전달
                val message = reader.readLine()

                // 클라이언트가 연결을 끊었을 때 (null 반환)
                if (message == null) {
                    log("클라이언트가 연결을 끊었습니다.")
                    break
                }

                log("클라이언트로부터 받은 메시지: $message")

                // send() : 사용자 공간에서 커널 공간으로 데이터를 전송
                writer.write("Echo: $message")
                writer.newLine()
                writer.flush()
            } catch (e: Exception) {
                log("클라이언트와의 연결에 오류가 발생했습니다: ${e.message}")
                break
            }
        }
        log("클라이언트와의 연결이 종료되었습니다.")
    }
}
