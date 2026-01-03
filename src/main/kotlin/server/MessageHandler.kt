package server

interface MessageHandler {
    fun onClientConnected(context: ClientContext)

    fun onMessageReceived(
        context: ClientContext,
        message: String,
    )

    fun onClientDisconnected(context: ClientContext)
}
