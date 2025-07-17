package blocking

import java.net.Socket

fun main() {
    println("클라이언트를 시작합니다...")

    /* 시스템 콜 흐름
     * socket() : 파일 디스크립터 생성 요청
     * connect() : 서버에 연결 요청 TCP 3-way 핸드쉐이크
     * close() : 소켓을 닫아 파일 디스크립터를 해제
     */
    Socket("127.0.0.1", 9999).use { socket ->
        println("서버에 연결되었습니다.")

        val writer = socket.getOutputStream().bufferedWriter()
        val reader = socket.getInputStream().bufferedReader()

        val messageToSend = "Hello, Kotlin Socket!"
        println("서버로 보낼 메시지: $messageToSend")

        // send() : 사용자 공간에서 커널 공간으로 데이터를 전송
        writer.write(messageToSend)
        writer.newLine()
        writer.flush()

        // recv() : 커널 공간에서 데이터를 읽어 사용자 공간으로 전달
        val receivedMessage = reader.readLine()
        println("서버로부터 받은 Echo: $receivedMessage")
    }
    println("클라이언트를 종료합니다.")
}
