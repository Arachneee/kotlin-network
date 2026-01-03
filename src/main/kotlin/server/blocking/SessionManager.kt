package server.blocking

import server.MessageHandler
import util.ThreadLogUtil.log
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class SessionManager(
    private val messageHandler: MessageHandler,
) {
    private val sessionIdCounter = AtomicLong(0)
    private val sessions = ConcurrentHashMap<Long, ClientSession>()
    private val isShuttingDown = AtomicBoolean(false)

    fun addClient(clientSocket: Socket): Long {
        val sessionId = sessionIdCounter.incrementAndGet()
        val clientSession = ClientSession.create(sessionId, clientSocket)

        sessions[sessionId] = clientSession
        connectClient(clientSession)
        return sessionId
    }

    private fun connectClient(clientSession: ClientSession) {
        val sessionId = clientSession.id
        val context = BlockingClientContext(clientSession, this)

        messageHandler.onClientConnected(context)

        while (!Thread.currentThread().isInterrupted) {
            try {
                val message = clientSession.reader.readLine() ?: break

                log("$sessionId 클라이언트로부터 받은 메시지: $message")
                messageHandler.onMessageReceived(context, message)
            } catch (e: Exception) {
                if (isShuttingDown.get()) {
                    log("$sessionId 서버 종료로 인한 연결 종료")
                } else {
                    log("$sessionId 클라이언트와의 연결에 오류가 발생했습니다: ${e.message}", e)
                }
                break
            }
        }

        messageHandler.onClientDisconnected(context)
        sessions.remove(sessionId)
        clientSession.close()
        log("클라이언트와의 연결이 종료되었습니다.")
    }

    fun broadcast(message: String) {
        sessions.values.forEach { session ->
            try {
                session.sendMessage(message)
            } catch (e: Exception) {
                log("메시지 전송 중 오류 발생: ${e.message}", e)
            }
        }
    }

    fun broadcastExcluding(
        excludeSessionId: Long,
        message: String,
    ) {
        sessions.values
            .filter { it.id != excludeSessionId }
            .forEach { session ->
                try {
                    session.sendMessage(message)
                } catch (e: Exception) {
                    log("메시지 전송 중 오류 발생: ${e.message}", e)
                }
            }
    }

    fun shutdown() {
        log("서버를 종료합니다. 모든 클라이언트 연결을 정리중...")
        isShuttingDown.set(true)

        sessions.values.forEach { session ->
            try {
                session.sendMessage("서버가 종료됩니다. 연결이 끊어집니다.")
                session.close()
            } catch (e: Exception) {
                log("클라이언트 ${session.id} 연결 종료 중 오류: ${e.message}", e)
            }
        }

        sessions.clear()
        log("모든 클라이언트 연결이 정리되었습니다.")
    }
}
