package application.chat

import server.ClientContext
import server.MessageHandler
import util.ThreadLogUtil.log

class ChatMessageHandler : MessageHandler {
    override fun onClientConnected(context: ClientContext) {
        log("[ChatApp] 클라이언트 입장: ${context.clientId}")
        context.broadcastExcludingSelf("[${context.clientId}] 님이 입장하셨습니다.\n")
        context.send("채팅방에 오신 것을 환영합니다!\n")
    }

    override fun onMessageReceived(
        context: ClientContext,
        message: String,
    ) {
        log("[ChatApp] 메시지 수신 [${context.clientId}]: $message")
        context.broadcastExcludingSelf("[${context.clientId}] $message\n")
    }

    override fun onClientDisconnected(context: ClientContext) {
        log("[ChatApp] 클라이언트 퇴장: ${context.clientId}")
        context.broadcastExcludingSelf("[${context.clientId}] 님이 퇴장하셨습니다.\n")
    }
}
