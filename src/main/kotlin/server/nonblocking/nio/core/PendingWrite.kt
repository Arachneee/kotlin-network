package server.nonblocking.nio.core

import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel

data class PendingWrite(
    val channel: SocketChannel,
    val buffer: ByteBuffer,
    val key: SelectionKey,
)
