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
        val clientSession = ClientSession.create(sessionId, clientSocket)

        sessions[sessionId] = clientSession
        connectClient(clientSession)
        return sessionId
    }

    private fun connectClient(clientSession: ClientSession) {
        val sessionId = clientSession.id

        while (!Thread.currentThread().isInterrupted) {
            try {
                // recv() : 커널 공간에서 데이터를 읽어 사용자 공간으로 전달
                val message = clientSession.reader.readLine() ?: break

                log("$sessionId 클라이언트로부터 받은 메시지: $message")
                broadcastMessage(sessionId, message)
            } catch (e: Exception) {
                log("$sessionId 클라이언트와의 연결에 오류가 발생했습니다: ${e.message}")
                break
            }
        }
        log("클라이언트와의 연결이 종료되었습니다.")
        sessions.remove(sessionId)
        clientSession.close()
    }

    private fun broadcastMessage(
        sessionId: Long,
        message: String,
    ) {
        sessions.values
            .filter { it.id != sessionId }
            .forEach { session ->
                try {
                    session.sendMessage("$sessionId: $message")
                } catch (e: Exception) {
                    log("메시지 전송 중 오류 발생: ${e.message}")
                }
            }
    }

    fun shutdown() {
        log("서버를 종료합니다. 모든 클라이언트 연결을 정리중...")

        sessions.values.forEach { session ->
            try {
                session.sendMessage("서버가 종료됩니다. 연결이 끊어집니다.")
                session.close()
            } catch (e: Exception) {
                log("클라이언트 ${session.id} 연결 종료 중 오류: ${e.message}")
            }
        }

        sessions.clear()
        log("모든 클라이언트 연결이 정리되었습니다.")
    }
}
