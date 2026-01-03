# 서버 아키텍처 설명

이 프로젝트에서는 세 가지 방식의 네트워크 서버를 구현하고 있습니다.

1. **BlockingServer** - 전통적인 블로킹 I/O 기반 멀티스레드 서버
2. **NioServer** - Java NIO Selector 기반 논블로킹 서버
3. **NettyServer** - Netty 프레임워크 기반 비동기 서버

---

## 1. BlockingServer (블로킹 I/O 서버)

### 개요
가장 전통적인 방식의 소켓 서버입니다. 각 클라이언트 연결마다 별도의 스레드를 할당하여 처리합니다.

### 동작 원리

```
┌─────────────────────────────────────────────────────────────────────┐
│                         BlockingServer                              │
│                                                                     │
│   ┌─────────────┐                                                   │
│   │ ServerSocket│  accept() 호출 시 블로킹                           │
│   │  (port)     │  → 새 클라이언트 연결까지 대기                       │
│   └──────┬──────┘                                                   │
│          │                                                          │
│          ▼                                                          │
│   ┌─────────────────┐                                               │
│   │   ThreadPool    │  10개의 스레드 풀                               │
│   │  (ExecutorService)                                              │
│   └────────┬────────┘                                               │
│            │                                                        │
│    ┌───────┼───────┬───────────┐                                    │
│    ▼       ▼       ▼           ▼                                    │
│ ┌──────┐ ┌──────┐ ┌──────┐  ┌──────┐                                │
│ │Thread│ │Thread│ │Thread│  │Thread│  각 스레드가 1개의 클라이언트 전담 │
│ │  #1  │ │  #2  │ │  #3  │  │ #10  │                                │
│ └──┬───┘ └──┬───┘ └──┬───┘  └──┬───┘                                │
│    │        │        │         │                                    │
│    ▼        ▼        ▼         ▼                                    │
│ ┌──────┐ ┌──────┐ ┌──────┐  ┌──────┐                                │
│ │Client│ │Client│ │Client│  │Client│                                │
│ │Session│ │Session│ │Session│ │Session│                             │
│ └──────┘ └──────┘ └──────┘  └──────┘                                │
└─────────────────────────────────────────────────────────────────────┘
```

### 핵심 컴포넌트

| 컴포넌트 | 역할 |
|---------|-----|
| `BlockingServer` | 메인 서버 클래스. ServerSocket으로 연결을 accept하고 스레드풀에 작업 위임 |
| `SessionManager` | 모든 클라이언트 세션을 관리. 브로드캐스트 기능 제공 |
| `ClientSession` | 개별 클라이언트의 소켓, Reader, Writer를 캡슐화 |
| `BlockingClientContext` | MessageHandler에 전달되는 클라이언트 컨텍스트 |

### 처리 흐름

1. `ServerSocket.accept()`가 새 클라이언트 연결을 수신 (블로킹)
2. 스레드풀에서 스레드를 할당받아 `SessionManager.addClient()` 실행
3. `ClientSession` 생성 후 `sessions` 맵에 등록
4. `reader.readLine()`으로 메시지 수신 대기 (블로킹)
5. 메시지 수신 시 `MessageHandler.onMessageReceived()` 호출
6. 연결 종료 시 세션 정리 및 리소스 해제

### 장점
- 구현이 직관적이고 이해하기 쉬움
- 디버깅이 용이함 (스레드별 스택 트레이스 확인 가능)
- 단순한 애플리케이션에 적합

### 단점
- 클라이언트 수 = 스레드 수 → 많은 동시 접속 시 리소스 낭비
- 컨텍스트 스위칭 오버헤드
- C10K 문제 (10,000개 동시 연결 처리 어려움)

---

## 2. NioServer (Java NIO 논블로킹 서버)

### 개요
Java NIO의 `Selector`를 사용하여 단일 스레드로 여러 클라이언트를 처리하는 논블로킹 서버입니다.

### 동작 원리

```
┌─────────────────────────────────────────────────────────────────────┐
│                           NioServer                                 │
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                       Event Loop                             │   │
│   │                    (Single Thread)                           │   │
│   │                                                              │   │
│   │         ┌───────────────────────────────────┐                │   │
│   │         │           Selector                │                │   │
│   │         │                                   │                │   │
│   │         │  ┌─────────┐  ┌─────────┐         │                │   │
│   │         │  │OP_ACCEPT│  │OP_READ  │         │                │   │
│   │         │  └────┬────┘  └────┬────┘         │                │   │
│   │         │       │            │              │                │   │
│   │         │  ┌────┴────┐  ┌────┴────┐         │                │   │
│   │         │  │OP_WRITE │  │         │         │                │   │
│   │         │  └─────────┘  └─────────┘         │                │   │
│   │         └───────────────────────────────────┘                │   │
│   │                          │                                   │   │
│   │                          ▼                                   │   │
│   │                   selector.select()                          │   │
│   │                   (이벤트 발생까지 대기)                        │   │
│   │                          │                                   │   │
│   │              ┌───────────┼───────────┐                       │   │
│   │              ▼           ▼           ▼                       │   │
│   │         ┌────────┐  ┌────────┐  ┌────────┐                   │   │
│   │         │handle  │  │handle  │  │handle  │                   │   │
│   │         │Accept  │  │Read    │  │Write   │                   │   │
│   │         └────────┘  └────────┘  └────────┘                   │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│   ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐                   │
│   │Channel 1│ │Channel 2│ │Channel 3│ │Channel N│                   │
│   │(Client) │ │(Client) │ │(Client) │ │(Client) │                   │
│   └─────────┘ └─────────┘ └─────────┘ └─────────┘                   │
└─────────────────────────────────────────────────────────────────────┘
```

### 핵심 컴포넌트

| 컴포넌트 | 역할 |
|---------|-----|
| `NioServer` | 메인 서버. Selector 기반 이벤트 루프 실행 |
| `ClientRegistry` | 연결된 모든 채널과 SelectionKey 관리 |
| `MessageReader` | 논블로킹 읽기 처리. 부분 메시지 버퍼링 |
| `MessageWriter` | 논블로킹 쓰기 처리. 대기 중인 쓰기 작업 관리 |
| `NioClientContext` | MessageHandler에 전달되는 클라이언트 컨텍스트 |

### 핵심 개념: SelectionKey 이벤트

| 이벤트 | 설명 |
|-------|-----|
| `OP_ACCEPT` | 새 클라이언트 연결 준비됨 |
| `OP_READ` | 클라이언트로부터 데이터 읽기 가능 |
| `OP_WRITE` | 클라이언트로 데이터 쓰기 가능 |

### 처리 흐름

1. `ServerSocketChannel`을 논블로킹 모드로 설정
2. `Selector`에 `OP_ACCEPT` 이벤트 등록
3. `selector.select()`로 이벤트 발생 대기
4. 이벤트 발생 시:
   - **OP_ACCEPT**: 새 클라이언트 채널 생성, `OP_READ` 등록
   - **OP_READ**: `MessageReader`로 데이터 읽기, 완전한 메시지면 핸들러 호출
   - **OP_WRITE**: `MessageWriter`로 대기 중인 데이터 전송
5. 다시 3번으로 돌아가 반복

### 부분 메시지 처리 (Partial Read)

논블로킹 I/O에서는 메시지가 한 번에 다 도착하지 않을 수 있습니다.

```
┌─────────────────────────────────────────────────────────────┐
│                    MessageReader                            │
│                                                             │
│   1차 read(): "Hell"     →  partialMessages에 저장          │
│   2차 read(): "o\n"      →  "Hello" 완성, 핸들러 호출        │
│                                                             │
│   ReadResult:                                               │
│   - Complete(message)  : 완전한 메시지 수신                  │
│   - Incomplete         : 아직 더 읽어야 함                   │
│   - ConnectionClosed   : 연결 종료됨                         │
└─────────────────────────────────────────────────────────────┘
```

### 장점
- 단일 스레드로 수천 개 연결 처리 가능
- 메모리 효율적 (스레드당 스택 메모리 절약)
- 컨텍스트 스위칭 오버헤드 없음

### 단점
- 구현 복잡도가 높음
- 버퍼 관리, 부분 읽기/쓰기 처리 필요
- CPU 집약적인 작업 시 모든 클라이언트에 영향

---

## 3. NettyServer (Netty 프레임워크 서버)

### 개요
Netty는 고성능 비동기 네트워크 프레임워크입니다. NIO의 복잡성을 추상화하고 다양한 프로토콜과 코덱을 제공합니다.

### 동작 원리

```
┌─────────────────────────────────────────────────────────────────────┐
│                          NettyServer                                │
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                     ServerBootstrap                          │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                               │                                     │
│              ┌────────────────┴────────────────┐                    │
│              ▼                                 ▼                    │
│   ┌──────────────────┐              ┌──────────────────┐            │
│   │    BossGroup     │              │   WorkerGroup    │            │
│   │  (1 EventLoop)   │              │ (N EventLoops)   │            │
│   │                  │              │                  │            │
│   │  연결 수락 담당    │              │  I/O 처리 담당    │            │
│   └────────┬─────────┘              └────────┬─────────┘            │
│            │                                 │                      │
│            │  accept                         │                      │
│            ▼                                 ▼                      │
│   ┌──────────────────────────────────────────────────────────────┐  │
│   │                      ChannelPipeline                         │  │
│   │                                                              │  │
│   │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │  │
│   │  │LineBasedFrame│→ │StringDecoder │→ │StringEncoder │        │  │
│   │  │   Decoder    │  │ (UTF-8)      │  │ (UTF-8)      │        │  │
│   │  └──────────────┘  └──────────────┘  └──────────────┘        │  │
│   │                           │                                  │  │
│   │                           ▼                                  │  │
│   │                  ┌────────────────────┐                      │  │
│   │                  │ NettyMessageHandler│                      │  │
│   │                  │  (비즈니스 로직)     │                      │  │
│   │                  └────────────────────┘                      │  │
│   └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                      ChannelGroup                            │   │
│   │        (연결된 모든 채널 관리, 브로드캐스트 지원)                 │   │
│   └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 핵심 컴포넌트

| 컴포넌트 | 역할 |
|---------|-----|
| `ServerBootstrap` | 서버 설정 및 부트스트랩 |
| `NioEventLoopGroup` | 이벤트 루프 스레드 그룹 |
| `ChannelPipeline` | 핸들러 체인 (인코더/디코더/비즈니스 로직) |
| `ChannelGroup` | 연결된 채널 그룹 관리 |
| `NettyMessageHandler` | 실제 메시지 처리 핸들러 |
| `NettyClientContext` | MessageHandler에 전달되는 클라이언트 컨텍스트 |

### EventLoopGroup 구조

| 그룹 | 스레드 수 | 역할 |
|-----|----------|-----|
| BossGroup | 1 | 새 연결 수락 (accept) |
| WorkerGroup | CPU 코어 수 × 2 | 실제 I/O 처리 (read/write) |

### ChannelPipeline 핸들러 체인

```
Inbound (수신):
  bytes → LineBasedFrameDecoder → StringDecoder → NettyMessageHandler

Outbound (송신):
  NettyMessageHandler → StringEncoder → bytes
```

| 핸들러 | 역할 |
|-------|-----|
| `LineBasedFrameDecoder` | 줄바꿈(\n) 기준으로 메시지 분리 |
| `StringDecoder` | 바이트를 UTF-8 문자열로 디코딩 |
| `StringEncoder` | 문자열을 UTF-8 바이트로 인코딩 |
| `NettyMessageHandler` | 비즈니스 로직 처리 |

### 이벤트 콜백

```kotlin
// 클라이언트 연결 시
override fun channelActive(ctx: ChannelHandlerContext)

// 메시지 수신 시
override fun channelRead0(ctx: ChannelHandlerContext, msg: String)

// 연결 종료 시
override fun channelInactive(ctx: ChannelHandlerContext)

// 예외 발생 시
override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable)
```

### 장점
- NIO 복잡성 추상화 (버퍼 관리, 부분 읽기 자동 처리)
- 풍부한 코덱 및 프로토콜 지원 (HTTP, WebSocket, SSL 등)
- 검증된 고성능 (Discord, Twitter 등에서 사용)
- 파이프라인 기반의 유연한 핸들러 구성

### 단점
- 학습 곡선이 있음
- 외부 의존성 추가
- 단순한 애플리케이션에는 과할 수 있음

---

## 비교 요약

| 항목 | BlockingServer | NioServer | NettyServer |
|-----|---------------|-----------|-------------|
| **I/O 모델** | 블로킹 | 논블로킹 | 논블로킹 |
| **스레드 모델** | 연결당 1스레드 | 단일 스레드 | EventLoop 스레드풀 |
| **동시 접속 한계** | 수백~수천 | 수만 | 수만~수십만 |
| **구현 복잡도** | 낮음 | 높음 | 중간 |
| **메모리 효율** | 낮음 | 높음 | 높음 |
| **적합한 상황** | 소규모, 학습용 | 커스텀 프로토콜 | 프로덕션 환경 |

---

## 공통 인터페이스: MessageHandler

모든 서버는 `MessageHandler` 인터페이스를 통해 비즈니스 로직을 주입받습니다.

```kotlin
interface MessageHandler {
    fun onClientConnected(context: ClientContext)
    fun onMessageReceived(context: ClientContext, message: String)
    fun onClientDisconnected(context: ClientContext)
}
```

이를 통해 Echo, Chat 등 다양한 애플리케이션 로직을 서버 구현과 독립적으로 작성할 수 있습니다.

