package server.nonblocking

import util.ThreadLogUtil.log
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel

fun main() {
    val selector = Selector.open() // 시스템 콜: selector 생성 (epoll_create, kqueue 등)
    val sessionChannelManager = SessionChannelManager()

    val serverSocketChannel = ServerSocketChannel.open() // 시스템 콜: socket() - 소켓 생성
    serverSocketChannel.bind(InetSocketAddress(9999)) // 시스템 콜: bind() + listen - 주소와 포트에 바인딩
    serverSocketChannel.configureBlocking(false) // 시스템 콜: fcntl() - non-blocking 모드 설정

    val selectionKey =
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT) // 시스템 콜: epoll_ctl, kqueue - selector에 채널 등록

    log("NIO 서버가 시작되었습니다. 연결을 기다립니다...")

    try {
        while (true) {
            // 1. 이벤트가 발생할 때까지 대기
            selector.select() // 시스템 콜: epoll_wait, kqueue - 이벤트 대기

            // 2. 발생한 이벤트들의 모음(Set)을 가져옵니다.
            val selectedKeys = selector.selectedKeys()
            val iterator = selectedKeys.iterator()

            while (iterator.hasNext()) {
                val key = iterator.next()
                iterator.remove()

                when {
                    key.isAcceptable -> handleAccept(key, selector, sessionChannelManager)
                    key.isReadable -> handleRead(key, sessionChannelManager)
                    key.isWritable -> handleWrite(key, sessionChannelManager)
                }
            }
        }
    } finally {
        selector.close() // 시스템 콜: close()
        serverSocketChannel.close() // 시스템 콜: close()
    }
}

private fun handleAccept(
    key: SelectionKey,
    selector: Selector,
    sessionChannelManager: SessionChannelManager,
) {
    val serverChannel = key.channel() as ServerSocketChannel
    val clientChannel = serverChannel.accept() // 시스템 콜: accept() - 클라이언트 연결 수락
    clientChannel.configureBlocking(false) // 시스템 콜: fcntl() - non-blocking 모드 설정
    sessionChannelManager.addClient(clientChannel)

    clientChannel.register(
        selector,
        SelectionKey.OP_READ,
    ) // 시스템 콜: epoll_ctl, kqueue - 클라이언트 채널을 selector에 등록
    log("새 클라이언트가 연결되었습니다: ${clientChannel.remoteAddress}")
}

private fun handleRead(
    key: SelectionKey,
    sessionChannelManager: SessionChannelManager,
) {
    try {
        sessionChannelManager.broadcastMessage(key) // 세션 매니저를 통해 메시지 브로드캐스트
    } catch (e: IOException) {
        key.cancel() // 시스템 콜: epoll_ctl, kqueue - selector에서 등록 해제
        log("클라이언트 연결이 비정상적으로 종료되었습니다.", e)
    }
}

private fun handleWrite(
    key: SelectionKey,
    sessionChannelManager: SessionChannelManager,
) {
    try {
        sessionChannelManager.handlePendingWrites(key)
    } catch (e: IOException) {
        key.cancel() // 시스템 콜: epoll_ctl, kqueue - selector에서 등록 해제
        log("쓰기 처리 중 오류가 발생했습니다.", e)
    }
}
