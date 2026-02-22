# 대호 I&T 사내 협업 플랫폼 — 백엔드

회의 녹음부터 STT 변환·요약·이슈 추적까지 전 생애주기를 자동화하는 사내 협업 도구의 백엔드 서버입니다.
<br>
<br>
## 프로젝트 데모
![프로젝트 데모1](./docs/daeho1.gif)
![프로젝트 데모2](./docs/daeho2.gif)
<br>
---

## 기술 스택

![Java](https://img.shields.io/badge/Java_17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)
![JPA](https://img.shields.io/badge/JPA-59666C?style=flat-square&logo=hibernate&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL_8-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis_7-DC382D?style=flat-square&logo=redis&logoColor=white)
![Firebase](https://img.shields.io/badge/FCM-FFCA28?style=flat-square&logo=firebase&logoColor=black)
![APNs](https://img.shields.io/badge/APNs-000000?style=flat-square&logo=apple&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)

---

## 시스템 아키텍처
![아키텍처](./docs/architecture.PNG)
---

## 주요 기능


---

## 데이터 모델

```mermaid
erDiagram
    member {
        bigint member_id PK
        varchar login_id
        varchar password
        varchar name
        varchar email
        varchar phone
        enum role "ADMIN, USER"
        bit is_employed
        bigint department_id FK
        bigint job_position_id FK
    }

    issue {
        bigint issue_id PK
        varchar title
        text content
        enum status "PLANNED, IN_PROGRESS, COMPLETED"
        date start_date
        date end_date
        bit is_private
        bit is_del
        bigint category_id FK
    }

    meeting {
        bigint meeting_id PK
        varchar title
        text content
        enum status "PLANNED, IN_PROGRESS, COMPLETED"
        datetime start_date
        datetime end_date
        bit is_private
        bit is_del
        bigint issue_id FK
        bigint category_id FK
    }

    stt {
        bigint stt_id PK
        enum status "RECORDING, ENCODING, ENCODED, PROCESSING, SUMMARIZING, COMPLETED"
        longtext content
        text summary
        varchar rid
        varchar summary_rid
        int chunking_cnt
        int retry_count
        bigint meeting_id FK
    }

    log {
        bigint log_id PK
        enum change_type "CREATE, UPDATE, DELETE"
        enum target_type "ISSUE, MEETING, COMMENT, STT, MEMBER"
        bigint target_id
        varchar title
        varchar update_field
        text message
        varchar member_name
    }

    notification {
        bigint notification_id PK
        varchar message
        varchar forward_url
        bit is_read
        bigint member_id FK
    }

    comment {
        bigint comment_id PK
        text content
        enum target_type "ISSUE, MEETING"
        bigint target_id
        bit is_del
        bigint member_id FK
    }

    file {
        bigint file_id PK
        varchar original_name
        varchar saved_name
        varchar path
        bigint size
        enum target_type "ISSUE, MEETING, STT, MEMBER, COMMENT"
        bigint target_id
    }

    member ||--o{ issue : "host"
    member ||--o{ notification : "receives"
    member ||--o{ comment : "writes"
    issue ||--o{ meeting : "has"
    meeting ||--o| stt : "has"
    issue ||--o{ log : "tracked_by"
    meeting ||--o{ log : "tracked_by"
```

---

## Getting Started

### 사전 요구사항

- Docker & Docker Compose
- [Daglo STT API](https://daglo.ai) 토큰
- Firebase 서비스 계정 키 (Chrome/Edge 푸시)
- VAPID 키 페어 (Web Push 표준)
- ffmpeg (오디오 인코딩 — 컨테이너 외부 실행 시)

### 환경변수 설정

`application.sample.properties`를 복사해 `application.properties`를 만들고 아래 항목을 채운다.

```properties
# DB
spring.datasource.url=jdbc:mysql://localhost:3306/daeho_db?useSSL=false&serverTimezone=Asia/Seoul&createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.repositories.enabled=false

# JWT
jwt.key=your_jwt_secret_key_min_32_chars

# Daglo STT
daglo.api.token=your_daglo_api_token
daglo.api.base-url=https://apis.daglo.ai
daglo.api.timeout=30000

# Web Push (VAPID)
vapid.public.key=your_vapid_public_key
vapid.private.key=your_vapid_private_key
vapid.service.mail=mailto:your@email.com

# STT Heartbeat TTL (초)
stt.recording.heartbeat-ttl-seconds=30

# 파일 저장 경로
file.location=/your/upload/path
ffmpeg.path=/usr/bin/ffmpeg
```

### Docker Compose로 실행

인프라(MySQL + Redis)만 컨테이너로 띄우고 애플리케이션을 로컬에서 실행하는 방법이다.

```bash
# 네트워크 생성 (최초 1회)
docker network create app-network

# MySQL + Redis 실행
docker compose up -d mysql redis

# 애플리케이션 빌드 및 실행
./gradlew bootRun
```

백엔드까지 포함해 전체를 컨테이너로 띄우려면:

```bash
docker compose up -d
```

> **포트**: Spring Boot `8080`, MySQL `3306`, Redis `6379`

## 프로젝트 구조

```
src/main/java/com/codehows/daehobe/
├── config/
│   ├── SpringSecurity/   # SecurityConfig, AuthEntryPoint, AccessHandler
│   └── jwtAuth/          # JwtFilter, JwtService
├── issue/                # 이슈 CRUD
├── meeting/              # 회의 CRUD
├── stt/
│   ├── controller/
│   ├── service/
│   │   └── processing/   # SttPollingScheduler, SttEncodingTaskExecutor
│   └── redis/            # HeartbeatExpirationListener
├── notification/         # FCM / APNs 분기 푸시
├── logging/
│   └── AOP/              # @TrackChanges, LoggingAspect
├── comment/
├── member/
└── file/
```
