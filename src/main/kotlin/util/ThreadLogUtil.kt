package util

object ThreadLogUtil {
    /**
     * 현재 스레드 정보를 포함하여 메시지를 출력합니다.
     * @param message 출력할 메시지
     */
    fun log(message: String) {
        val threadName = Thread.currentThread().name
        println("[$threadName] $message")
    }

    fun log(
        message: String,
        throwable: Throwable,
    ) {
        val threadName = Thread.currentThread().name
        println("[$threadName] $message")
        throwable.printStackTrace()
    }

    fun logThreadInfo() {
        val thread = Thread.currentThread()
        println("=== 스레드 정보 ===")
        println("이름: ${thread.name}")
        println("ID: ${thread.id}")
        println("우선순위: ${thread.priority}")
        println("상태: ${thread.state}")
        println("데몬 여부: ${thread.isDaemon}")
        println("================")
    }
}
