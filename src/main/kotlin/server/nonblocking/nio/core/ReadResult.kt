package server.nonblocking.nio.core

sealed class ReadResult {
    object ConnectionClosed : ReadResult()

    object Incomplete : ReadResult()

    data class Complete(
        val message: String,
    ) : ReadResult()
}
