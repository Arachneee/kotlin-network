package server.nonblocking.netty

import io.netty.channel.Channel
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.ChannelMatchers
import server.ClientContext

class NettyClientContext(
    private val channel: Channel,
    private val channelGroup: ChannelGroup,
) : ClientContext {
    override val clientId: String
        get() = channel.remoteAddress().toString()

    override fun send(message: String) {
        channel.writeAndFlush(message)
    }

    override fun broadcast(message: String) {
        channelGroup.writeAndFlush(message)
    }

    override fun broadcastExcludingSelf(message: String) {
        channelGroup.writeAndFlush(message, ChannelMatchers.isNot(channel))
    }
}
