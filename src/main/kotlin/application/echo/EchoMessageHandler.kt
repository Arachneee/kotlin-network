package application.echo

import server.ClientContext
import server.MessageHandler
import util.ThreadLogUtil.log

class EchoMessageHandler : MessageHandler {
    override fun onClientConnected(context: ClientContext) {
        log("클라이언트 연결됨: ${context.clientId}")
    }

    override fun onMessageReceived(
        context: ClientContext,
        message: String,
    ) {
        log("메시지 수신: $message")
        context.send("$message\n")
    }

    override fun onClientDisconnected(context: ClientContext) {
        log("클라이언트 연결 해제됨: ${context.clientId}")
    }
}
