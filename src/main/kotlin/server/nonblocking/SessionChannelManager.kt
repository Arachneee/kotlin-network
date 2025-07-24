package server.nonblocking

import util.ThreadLogUtil.log
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap

// 메시지 읽기 결과를 명확하게 구분하기 위한 sealed class
sealed class ReadResult {
    object ConnectionClosed : ReadResult() // 연결 종료

    object Incomplete : ReadResult() // 아직 완전한 메시지가 아님

    data class Complete(
        val message: String,
    ) : ReadResult() // 완전한 메시지 수신
}

data class PendingWrite(
    val channel: SocketChannel,
    val buffer: ByteBuffer,
    val key: SelectionKey,
)

class SessionChannelManager {
    private val clients = ConcurrentHashMap<SocketChannel, ByteBuffer>()
    private val partialMessages = ConcurrentHashMap<SocketChannel, StringBuilder>()
    private val pendingWrites = mutableListOf<PendingWrite>()

    fun addClient(clientChannel: SocketChannel) {
        clients[clientChannel] = ByteBuffer.allocate(1024)
    }

    fun broadcastMessage(key: SelectionKey) {
        val senderChannel = key.channel() as SocketChannel
        val senderBuffer = clients[senderChannel] ?: return

        // 완전한 메시지를 읽을 때까지 대기
        val message = readCompleteMessage(senderChannel, senderBuffer)

        when (message) {
            is ReadResult.ConnectionClosed -> {
                // 연결이 종료됨
                removeClient(senderChannel, key)
                log("클라이언트 연결이 종료되었습니다.")
                return
            }
            is ReadResult.Incomplete -> return
            is ReadResult.Complete -> {
                log("받은 완전한 메시지: ${message.message}")

                // 모든 클라이언트에게 브로드캐스트 (발신자 제외)
                val broadcastMessage = "$senderChannel : ${message.message}\n"
                val messageBytes = broadcastMessage.toByteArray(Charsets.UTF_8)

                clients.keys
                    .filter { it != senderChannel }
                    .forEach { clientChannel ->
                        if (!safeWrite(clientChannel, messageBytes, key)) {
                            log("클라이언트 ${clientChannel.remoteAddress}에게 즉시 전송 실패 - 대기열에 추가됨")
                        }
                    }
            }
        }
    }

    fun readCompleteMessage(
        channel: SocketChannel,
        buffer: ByteBuffer,
    ): ReadResult {
        val bytesRead = channel.read(buffer)

        if (bytesRead == -1) return ReadResult.ConnectionClosed
        if (bytesRead == 0) return ReadResult.Incomplete

        buffer.flip()
        val newData = Charsets.UTF_8.decode(buffer).toString()
        buffer.clear()
        log("새로운 데이터 읽음 : $newData")

        // 이전에 부분적으로 읽은 데이터와 합치기
        val partialMessage = partialMessages.getOrPut(channel) { StringBuilder() }
        partialMessage.append(newData)

        // 메시지 끝을 나타내는 구분자 확인 (예: 줄바꿈)
        val message = partialMessage.toString()
        val lineEndIndex = message.indexOf('\n')

        return if (lineEndIndex != -1) {
            // 완전한 메시지를 찾음
            val completeMessage = message.substring(0, lineEndIndex)

            // 남은 데이터 처리
            val remaining = message.substring(lineEndIndex + 1)
            if (remaining.isEmpty()) {
                partialMessages.remove(channel)
            } else {
                partialMessage.clear().append(remaining)
            }

            ReadResult.Complete(completeMessage)
        } else {
            // 아직 완전한 메시지가 아님 - 더 기다려야 함
            ReadResult.Incomplete
        }
    }

    fun safeWrite(
        channel: SocketChannel,
        data: ByteArray,
        key: SelectionKey,
    ): Boolean {
        val buffer = ByteBuffer.wrap(data)

        return try {
            while (buffer.hasRemaining()) {
                val bytesWritten = channel.write(buffer)

                if (bytesWritten == 0) {
                    // 소켓 버퍼가 가득 찬 상태 - 나중에 처리하기 위해 저장
                    synchronized(pendingWrites) {
                        pendingWrites.add(PendingWrite(channel, buffer, key))
                    }

                    // WRITE 이벤트에 관심 등록
                    key.interestOps(key.interestOps() or SelectionKey.OP_WRITE)
                    return false // 완전히 쓰지 못함
                }
            }
            true // 모든 데이터 전송 완료
        } catch (e: Exception) {
            log("쓰기 중 오류 발생: ${e.message}")
            removeClient(channel, key)
            false
        }
    }

    // Selector에서 WRITE 이벤트가 발생했을 때 호출
    fun handlePendingWrites(key: SelectionKey) {
        val channel = key.channel() as SocketChannel

        synchronized(pendingWrites) {
            val iterator = pendingWrites.iterator()

            while (iterator.hasNext()) {
                val pendingWrite = iterator.next()
                if (pendingWrite.channel == channel) {
                    val buffer = pendingWrite.buffer

                    try {
                        while (buffer.hasRemaining()) {
                            val bytesWritten = channel.write(buffer)
                            if (bytesWritten == 0) {
                                // 여전히 쓸 수 없음 - 다음 WRITE 이벤트를 기다림
                                return
                            }
                        }

                        // 모든 데이터 전송 완료
                        iterator.remove()
                    } catch (e: Exception) {
                        log("pending write 처리 중 오류: ${e.message}")
                        iterator.remove()
                        removeClient(channel, key)
                        return
                    }
                }
            }

            // 더 이상 해당 채널의 pending write가 없으면 WRITE 이벤트 제거
            if (pendingWrites.none { it.channel == channel }) {
                key.interestOps(key.interestOps() and SelectionKey.OP_WRITE.inv())
            }
        }
    }

    fun removeClient(
        clientChannel: SocketChannel,
        key: SelectionKey? = null,
    ) {
        clients.remove(clientChannel)
        partialMessages.remove(clientChannel)

        // pending writes에서도 제거
        pendingWrites.removeAll { it.channel == clientChannel }

        try {
            clientChannel.close()
        } catch (e: Exception) {
            log("클라이언트 채널 닫기 실패: ${e.message}")
        }

        key?.cancel()
    }
}
