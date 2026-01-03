package server.blocking

import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.Socket

class ClientSession(
    val id: Long,
    val writer: BufferedWriter,
    val reader: BufferedReader,
    val socket: Socket,
) {
    @Synchronized
    fun sendMessage(message: String) {
        writer.write(message)
        writer.newLine()
        writer.flush()
    }

    fun close() {
        try {
            writer.close()
        } catch (e: Exception) {
        }

        try {
            reader.close()
        } catch (e: Exception) {
        }

        try {
            socket.close()
        } catch (e: Exception) {
        }
    }

    companion object {
        fun create(
            sessionId: Long,
            clientSocket: Socket,
        ) = ClientSession(
            id = sessionId,
            writer = clientSocket.outputStream.bufferedWriter(),
            reader = clientSocket.inputStream.bufferedReader(),
            socket = clientSocket,
        )
    }
}
