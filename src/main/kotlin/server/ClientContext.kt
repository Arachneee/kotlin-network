package server

interface ClientContext {
    val clientId: String

    fun send(message: String)

    fun broadcast(message: String)

    fun broadcastExcludingSelf(message: String)
}
