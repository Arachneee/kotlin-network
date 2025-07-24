package server.nonblocking

import util.ThreadLogUtil.log
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel

class SessionChannelManager {
    private val clients = mutableMapOf<SocketChannel, ByteBuffer>()

    fun addClient(clientChannel: SocketChannel) {
        clients[clientChannel] = ByteBuffer.allocate(1024)
    }

    fun broadcastMessage(key: SelectionKey) {
        val senderChannel = key.channel() as SocketChannel
        val senderBuffer = clients[senderChannel] ?: return
        val bytesRead = senderChannel.read(senderBuffer)

        if (bytesRead == -1) {
            clients.remove(senderChannel)
            senderChannel.close() // 시스템 콜: close() - 소켓 닫기
            key.cancel()
            log("클라이언트 연결이 종료되었습니다.")
        } else {
            senderBuffer.flip() // 버퍼를 '읽기 모드'로 전환
            val received = Charsets.UTF_8.decode(senderBuffer).toString()
            log("받은 메시지: $received")

            // 모든 클라이언트에게 브로드캐스트 (발신자 제외)
            val messageBytes = "$senderChannel : $received".toByteArray(Charsets.UTF_8)

            clients
                .filter { it.key != senderChannel } // 발신자 제외
                .forEach { clientChannel, clientBuffer ->
                    try {
                        clientBuffer.clear()
                        clientBuffer.put(messageBytes)
                        clientBuffer.flip() // 쓰기 모드로 전환

                        while (clientBuffer.hasRemaining()) {
                            clientChannel.write(clientBuffer)
                        }
                    } catch (e: Exception) {
                        log("브로드캐스트 중 오류 발생: ${e.message}")
                        clients.remove(clientChannel)
                        try {
                            clientChannel.close()
                        } catch (closeException: Exception) {
                        }
                    }
                }

            // 원본 버퍼 정리
            senderBuffer.clear()
        }
    }
}
