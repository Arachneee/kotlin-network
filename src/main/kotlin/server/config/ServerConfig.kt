package server.config

import org.yaml.snakeyaml.Yaml
import java.io.InputStream

data class ServerConfig(
    val port: Int,
    val type: ServerType,
    val handler: HandlerType,
) {
    companion object {
        fun load(): ServerConfig {
            val yaml = Yaml()
            val inputStream: InputStream = ServerConfig::class.java.classLoader
                .getResourceAsStream("application.yml")
                ?: throw IllegalStateException("application.yml 파일을 찾을 수 없습니다.")

            val config: Map<String, Any> = yaml.load(inputStream)
            val serverConfig = config["server"] as Map<String, Any>

            return ServerConfig(
                port = serverConfig["port"] as Int,
                type = ServerType.from(serverConfig["type"] as String),
                handler = HandlerType.from(serverConfig["handler"] as String),
            )
        }
    }
}

enum class ServerType {
    NETTY,
    NIO,
    BLOCKING,
    ;

    companion object {
        fun from(value: String): ServerType =
            when (value.lowercase()) {
                "netty" -> NETTY
                "nio" -> NIO
                "blocking" -> BLOCKING
                else -> throw IllegalArgumentException("지원하지 않는 서버 타입입니다: $value (netty, nio, blocking 중 선택)")
            }
    }
}

enum class HandlerType {
    CHAT,
    ECHO,
    ;

    companion object {
        fun from(value: String): HandlerType =
            when (value.lowercase()) {
                "chat" -> CHAT
                "echo" -> ECHO
                else -> throw IllegalArgumentException("지원하지 않는 핸들러 타입입니다: $value (chat, echo 중 선택)")
            }
    }
}

