package server.blocking

import server.ClientContext

class BlockingClientContext(
    private val session: ClientSession,
    private val sessionManager: SessionManager,
) : ClientContext {
    override val clientId: String
        get() = session.id.toString()

    override fun send(message: String) {
        session.sendMessage(message)
    }

    override fun broadcast(message: String) {
        sessionManager.broadcast(message)
    }

    override fun broadcastExcludingSelf(message: String) {
        sessionManager.broadcastExcluding(session.id, message)
    }
}
