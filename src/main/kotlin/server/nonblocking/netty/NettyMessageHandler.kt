package server.nonblocking.netty

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.group.ChannelGroup
import server.MessageHandler
import util.ThreadLogUtil.log

class NettyMessageHandler(
    private val channelGroup: ChannelGroup,
    private val messageHandler: MessageHandler,
) : SimpleChannelInboundHandler<String>() {
    override fun channelActive(ctx: ChannelHandlerContext) {
        val context = createContext(ctx)

        channelGroup.add(ctx.channel())

        messageHandler.onClientConnected(context)

        log("[Netty] 클라이언트 연결됨: ${context.clientId} (현재 ${channelGroup.size}명)")
    }

    override fun channelRead0(
        ctx: ChannelHandlerContext,
        msg: String,
    ) {
        val context = createContext(ctx)
        messageHandler.onMessageReceived(context, msg)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val context = createContext(ctx)

        messageHandler.onClientDisconnected(context)

        log("[Netty] 클라이언트 연결 해제됨: ${context.clientId} (현재 ${channelGroup.size}명)")
    }

    override fun exceptionCaught(
        ctx: ChannelHandlerContext,
        cause: Throwable,
    ) {
        log("[Netty] 오류 발생: ${cause.message}")
        ctx.close()
    }

    private fun createContext(ctx: ChannelHandlerContext): NettyClientContext = NettyClientContext(ctx.channel(), channelGroup)
}
