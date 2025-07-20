package blocking

import util.ThreadLogUtil.log
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class SessionManager {
    private val sessionIdCounter = AtomicLong(0)
    private val sessions = ConcurrentHashMap<Long, ClientSession>()

    fun addClient(clientSocket: Socket): Long {
        val sessionId = sessionIdCounter.incrementAndGet()
        sessions[sessionId] = ClientSession(sessionId, clientSocket)

        connectClient(sessionId, clientSocket)
        return sessionId
    }

    private fun connectClient(
        sessionId: Long,
        clientSocket: Socket,
    ) {
        clientSocket.use { socket ->
            val reader = socket.inputStream.bufferedReader()

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

                    log("$sessionId 클라이언트로부터 받은 메시지: $message")
                    broadcastMessage(sessionId, message)
                } catch (e: Exception) {
                    log("$sessionId 클라이언트와의 연결에 오류가 발생했습니다: ${e.message}")
                    break
                }
            }
            log("클라이언트와의 연결이 종료되었습니다.")
            removeClient(sessionId)
        }
    }

    fun removeClient(sessionId: Long) {
        sessions.remove(sessionId)
    }

    private fun broadcastMessage(
        sessionId: Long,
        message: String,
    ) {
        sessions.values
            .filter { it.id != sessionId }
            .forEach { session ->
                try {
                    val writer = session.socket.getOutputStream().bufferedWriter()
                    writer.write("$sessionId: $message")
                    writer.newLine()
                    writer.flush()
                } catch (e: Exception) {
                    log("메시지 전송 중 오류 발생: ${e.message}")
                }
            }
    }

    data class ClientSession(
        val id: Long,
        val socket: Socket,
    )
}
