# Kotlin Network Server

Kotlin으로 구현한 네트워크 서버 프레임워크입니다. 다양한 I/O 모델(Blocking, NIO, Netty)과 메시지 핸들러(Echo, Chat)를 지원합니다.

## 주요 기능

- **3가지 서버 타입 지원**
  - `blocking`: 전통적인 Thread-per-Client 블로킹 I/O 방식
  - `nio`: Java NIO Selector 기반 논블로킹 I/O 방식
  - `netty`: Netty 프레임워크 기반 고성능 비동기 I/O 방식

- **2가지 메시지 핸들러 지원**
  - `echo`: 받은 메시지를 그대로 클라이언트에게 반환
  - `chat`: 다중 클라이언트 채팅 기능 (브로드캐스트)

## 기술 스택

- Kotlin 2.1.21
- JDK 21
- Netty 4.1.117
- SnakeYAML 2.2
- Gradle (Kotlin DSL)

## 프로젝트 구조

```
src/main/kotlin/
├── application/
│   ├── KotlinNetworkApplication.kt    # 애플리케이션 진입점
│   ├── chat/
│   │   └── ChatMessageHandler.kt      # 채팅 핸들러
│   └── echo/
│       └── EchoMessageHandler.kt      # 에코 핸들러
├── client/
│   └── Client.kt                      # 테스트용 클라이언트
├── server/
│   ├── ClientContext.kt               # 클라이언트 컨텍스트 인터페이스
│   ├── MessageHandler.kt              # 메시지 핸들러 인터페이스
│   ├── blocking/
│   │   ├── BlockingServer.kt          # 블로킹 서버 구현
│   │   ├── BlockingClientContext.kt
│   │   ├── ClientSession.kt
│   │   └── SessionManager.kt
│   ├── config/
│   │   └── ServerConfig.kt            # 서버 설정 로더
│   └── nonblocking/
│       ├── netty/
│       │   ├── NettyServer.kt         # Netty 서버 구현
│       │   ├── NettyClientContext.kt
│       │   └── NettyMessageHandler.kt
│       └── nio/
│           ├── NioServer.kt           # NIO 서버 구현
│           ├── NioClientContext.kt
│           └── core/
│               ├── ClientRegistry.kt
│               ├── MessageReader.kt
│               ├── MessageWriter.kt
│               └── ...
└── util/
    └── ThreadLogUtil.kt               # 스레드 로깅 유틸
```

## 설정

`src/main/resources/application.yml` 파일에서 서버 설정을 변경할 수 있습니다:

```yaml
server:
  port: 9999
  type: netty  # netty, nio, blocking 중 선택
  handler: chat   # chat, echo 중 선택
```

## 실행 방법

### 서버 실행

```bash
./gradlew run
```

또는 `application/KotlinNetworkApplication.kt`의 `main()` 함수를 실행합니다.

### 클라이언트 실행

`client/Client.kt`의 `main()` 함수를 실행합니다.

클라이언트 실행 후:
- 메시지를 입력하고 Enter를 누르면 서버로 전송됩니다
- `exit`을 입력하면 클라이언트가 종료됩니다

## 아키텍처

### 서버 타입별 특징

| 타입 | 스레드 모델 | 특징 |
|------|-------------|------|
| Blocking | Thread-per-Client | 구현이 간단, 동시 접속 수에 따라 스레드 수 증가 |
| NIO | Single Thread (Event Loop) | Selector 기반, 적은 스레드로 다수 연결 처리 |
| Netty | Boss/Worker Thread | 고성능, 풍부한 기능, 프로덕션 레디 |

### 핵심 인터페이스

**MessageHandler** - 메시지 처리 로직 정의

```kotlin
interface MessageHandler {
    fun onClientConnected(context: ClientContext)
    fun onMessageReceived(context: ClientContext, message: String)
    fun onClientDisconnected(context: ClientContext)
}
```

**ClientContext** - 클라이언트 통신 추상화

```kotlin
interface ClientContext {
    val clientId: String
    fun send(message: String)
    fun broadcast(message: String)
    fun broadcastExcludingSelf(message: String)
}
```

## 테스트

```bash
# JDK 21 활성화 (SDKMAN 사용 시)
source ~/.sdkman/bin/sdkman-init.sh && sdk use java 21-tem

# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "TestClassName"
```

## 요구사항

- JDK 21
- Gradle 8.x+

## 라이센스

MIT License

