package server.nonblocking.netty

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LineBasedFrameDecoder
import io.netty.handler.codec.string.StringDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.util.CharsetUtil
import io.netty.util.concurrent.GlobalEventExecutor
import server.MessageHandler
import util.ThreadLogUtil.log

class NettyServer(
    private val port: Int,
    private val messageHandler: MessageHandler,
) {
    private val channelGroup = DefaultChannelGroup(GlobalEventExecutor.INSTANCE)

    fun start() {
        val bossGroup = NioEventLoopGroup(1)
        val workerGroup = NioEventLoopGroup()

        try {
            val bootstrap = ServerBootstrap()

            bootstrap
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(
                    object : ChannelInitializer<SocketChannel>() {
                        override fun initChannel(ch: SocketChannel) {
                            ch
                                .pipeline()
                                .addLast(LineBasedFrameDecoder(1024))
                                .addLast(StringDecoder(CharsetUtil.UTF_8))
                                .addLast(StringEncoder(CharsetUtil.UTF_8))
                                .addLast(NettyMessageHandler(channelGroup, messageHandler))
                        }
                    },
                )

            log("Netty 서버가 포트 $port 에서 시작되었습니다.")

            val channelFuture = bootstrap.bind(port).sync()
            channelFuture.channel().closeFuture().sync()
        } finally {
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
    }
}
