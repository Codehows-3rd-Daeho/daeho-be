# DX Issue Manager

이슈 관리 기반 협업 플랫폼 - Backend API Server

## Overview

| 항목      | 내용                                                                     |
| --------- | ------------------------------------------------------------------------ |
| 개발 기간 | 2025.09 ~ 2026.01                                                        |
| 구성 인원 | 4명 (PM/PL 1명, FE/BE 3명)                                               |
| GitHub    | [Codehows-3rd-Daeho](https://github.com/Codehows-3rd-Daeho/repositories) |

## Tech Stack

**Backend**

- Java 21, Spring Boot 3.4
- Spring Security, Spring AOP, JPA
- WebSocket, Web Push

**Database & Cache**

- MySQL 8.0, Redis

**DevOps**

- Docker, Jenkins, Nginx

**Frontend**

- TypeScript, React.js

## Features

- **이슈 관리** - 이슈 생성/수정/삭제, 상태 추적
- **회의 관리** - 회의 일정 및 참여자 관리
- **실시간 알림** - WebSocket & Web Push 기반 알림
- **STT** - 음성 인식 기능
- **파일 관리** - 이미지/파일 업로드
- **조직 관리** - 부서, 그룹, 직급 관리

## Project Structure

```
src/main/java/com/codehows/daehobe/
├── comment/        # 댓글
├── file/           # 파일 업로드
├── issue/          # 이슈 관리
├── logging/        # 로깅
├── masterData/     # 기준정보 (부서, 그룹, 카테고리 등)
├── meeting/        # 회의 관리
├── member/         # 회원 관리
├── notification/   # 알림 (Web Push)
└── stt/            # Speech-to-Text
```

### Prerequisites

- Java 21
- Docker & Docker Compose

## API Endpoints

| Domain       | Endpoint             | Description |
| ------------ | -------------------- | ----------- |
| Issue        | `/api/issues`        | 이슈 CRUD   |
| Meeting      | `/api/meetings`      | 회의 관리   |
| Member       | `/api/members`       | 회원 관리   |
| Comment      | `/api/comments`      | 댓글        |
| Notification | `/api/notifications` | 알림        |
