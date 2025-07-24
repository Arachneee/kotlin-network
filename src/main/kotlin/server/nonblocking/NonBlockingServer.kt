package server.nonblocking

import util.ThreadLogUtil.log
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

fun main() {
    val selector = Selector.open() // 시스템 콜: selector 생성 (epoll_create, kqueue 등)

    val serverSocketChannel = ServerSocketChannel.open() // 시스템 콜: socket() - 소켓 생성
    serverSocketChannel.bind(InetSocketAddress(9999)) // 시스템 콜: bind() - 주소와 포트에 바인딩
    serverSocketChannel.configureBlocking(false) // 시스템 콜: fcntl() - non-blocking 모드 설정

    val selectionKey =
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT) // 시스템 콜: epoll_ctl, kqueue - selector에 채널 등록

    log("NIO 서버가 시작되었습니다. 연결을 기다립니다...")

    while (true) {
        // 1. 이벤트가 발생할 때까지 대기
        selector.select() // 시스템 콜: epoll_wait, kqueue - 이벤트 대기

        // 2. 발생한 이벤트들의 모음(Set)을 가져옵니다.
        val selectedKeys = selector.selectedKeys()
        val iterator = selectedKeys.iterator()

        while (iterator.hasNext()) {
            val key = iterator.next()

            // 1. '연결 요청' 이벤트 처리
            if (key.isAcceptable) {
                val serverChannel = key.channel() as ServerSocketChannel
                val clientChannel = serverChannel.accept() // 시스템 콜: accept() - 클라이언트 연결 수락
                clientChannel.configureBlocking(false) // 시스템 콜: fcntl() - non-blocking 모드 설정

                clientChannel.register(
                    selector,
                    SelectionKey.OP_READ,
                ) // 시스템 콜: epoll_ctl, kqueue - 클라이언트 채널을 selector에 등록
                log("새 클라이언트가 연결되었습니다: ${clientChannel.remoteAddress}")

                // 2. '데이터 읽기' 이벤트 처리
            } else if (key.isReadable) {
                val clientChannel = key.channel() as SocketChannel
                val buffer = ByteBuffer.allocate(1024)

                try {
                    // 채널에서 데이터를 읽어 버퍼에 담음
                    val bytesRead = clientChannel.read(buffer) // 시스템 콜: read() - 소켓에서 데이터 읽기

                    if (bytesRead == -1) {
                        key.cancel() // 시스템 콜: epoll_ctl, kqueue - selector에서 등록 해제
                        clientChannel.close() // 시스템 콜: close() - 소켓 닫기
                        log("클라이언트 연결이 종료되었습니다.")
                    } else {
                        buffer.flip() // 버퍼를 '읽기 모드'로 전환
                        val received = Charsets.UTF_8.decode(buffer).toString()
                        log("받은 메시지: $received")

                        buffer.flip()
                        clientChannel.write(buffer) // 시스템 콜: write() - 소켓에 데이터 쓰기
                    }
                } catch (e: IOException) {
                    key.cancel() // 시스템 콜: epoll_ctl, kqueue - selector에서 등록 해제
                    clientChannel.close() // 시스템 콜: close() - 소켓 닫기
                    log("클라이언트 연결이 비정상적으로 종료되었습니다.", e)
                }
            }

            iterator.remove()
        }
    }
}
