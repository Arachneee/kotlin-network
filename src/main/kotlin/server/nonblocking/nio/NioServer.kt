package server.nonblocking.nio

import server.MessageHandler
import server.nonblocking.nio.core.ClientRegistry
import server.nonblocking.nio.core.MessageReader
import server.nonblocking.nio.core.MessageWriter
import server.nonblocking.nio.core.ReadResult
import util.ThreadLogUtil.log
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap

class NioServer(
    private val port: Int,
    private val messageHandler: MessageHandler,
) {
    private val selector: Selector = Selector.open()
    private val serverChannel: ServerSocketChannel = ServerSocketChannel.open()
    private val messageReader = MessageReader()
    private val messageWriter = MessageWriter()
    private val clientRegistry = ClientRegistry(messageReader, messageWriter)

    private val channelToClientId = ConcurrentHashMap<SocketChannel, String>()

    fun start() {
        initializeServerChannel()
        log("NIO 서버가 포트 $port 에서 시작되었습니다.")

        try {
            runEventLoop()
        } finally {
            shutdown()
        }
    }

    private fun initializeServerChannel() {
        serverChannel.bind(InetSocketAddress(port))
        serverChannel.configureBlocking(false)
        serverChannel.register(selector, SelectionKey.OP_ACCEPT)
    }

    private fun runEventLoop() {
        while (true) {
            selector.select()
            processSelectedKeys()
        }
    }

    private fun processSelectedKeys() {
        val iterator = selector.selectedKeys().iterator()

        while (iterator.hasNext()) {
            val key = iterator.next()
            iterator.remove()

            handleKey(key)
        }
    }

    private fun handleKey(key: SelectionKey) {
        when {
            key.isAcceptable -> handleAccept()
            key.isReadable -> handleRead(key)
            key.isWritable -> handleWrite(key)
        }
    }

    private fun handleAccept() {
        log("handleAccept")
        val clientChannel = serverChannel.accept()
        clientChannel.configureBlocking(false)

        val key = clientChannel.register(selector, SelectionKey.OP_READ)
        clientRegistry.register(clientChannel, key)

        val clientId = generateClientId(clientChannel)
        channelToClientId[clientChannel] = clientId

        val context = createContext(clientChannel)
        messageHandler.onClientConnected(context)

        log("새 클라이언트가 연결되었습니다: $clientId")
    }

    private fun handleRead(key: SelectionKey) {
        log("handleRead")
        val channel = key.channel() as SocketChannel

        try {
            when (val result = messageReader.read(channel)) {
                is ReadResult.ConnectionClosed -> {
                    handleDisconnect(channel)
                }

                is ReadResult.Incomplete -> {
                    return
                }

                is ReadResult.Complete -> {
                    val context = createContext(channel)
                    messageHandler.onMessageReceived(context, result.message)
                }
            }
        } catch (e: IOException) {
            handleDisconnect(channel)
            log("클라이언트 연결이 비정상적으로 종료되었습니다.", e)
        }
    }

    private fun handleWrite(key: SelectionKey) {
        log("handleWrite")
        val channel = key.channel() as SocketChannel

        try {
            messageWriter.processPendingWrites(key)
        } catch (e: IOException) {
            handleDisconnect(channel)
            log("쓰기 처리 중 오류가 발생했습니다.", e)
        }
    }

    private fun handleDisconnect(channel: SocketChannel) {
        val context = createContext(channel)
        messageHandler.onClientDisconnected(context)

        channelToClientId.remove(channel)
        clientRegistry.unregister(channel)
    }

    private fun createContext(channel: SocketChannel): NioClientContext =
        NioClientContext(
            channel,
            clientRegistry,
            messageWriter,
            channelToClientId,
        )

    private fun generateClientId(channel: SocketChannel): String = channel.remoteAddress.toString()

    private fun shutdown() {
        selector.close()
        serverChannel.close()
    }
}
