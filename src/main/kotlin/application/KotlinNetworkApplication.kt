package application

import application.chat.ChatMessageHandler
import application.echo.EchoMessageHandler
import server.MessageHandler
import server.blocking.BlockingServer
import server.config.HandlerType
import server.config.ServerConfig
import server.config.ServerType
import server.nonblocking.netty.NettyServer
import server.nonblocking.nio.NioServer
import util.ThreadLogUtil.log

fun main() {
    val config = ServerConfig.load()
    log("서버 설정: type=${config.type}, handler=${config.handler}, port=${config.port}")

    val messageHandler = createMessageHandler(config.handler)

    when (config.type) {
        ServerType.NETTY -> NettyServer(config.port, messageHandler).start()
        ServerType.NIO -> NioServer(config.port, messageHandler).start()
        ServerType.BLOCKING -> BlockingServer(config.port, messageHandler).start()
    }
}

private fun createMessageHandler(handlerType: HandlerType): MessageHandler =
    when (handlerType) {
        HandlerType.CHAT -> ChatMessageHandler()
        HandlerType.ECHO -> EchoMessageHandler()
    }
