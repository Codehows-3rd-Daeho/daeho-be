# H5: 구독 유효성 검증

## 1. 문제 상황
- **현상**: WebPush 구독 저장 시 유효성 검증 없음
- **영향 범위**: 잘못된 구독 정보 누적, 전송 실패 증가
- **코드 위치**: `WebPushService.java:46-58`

## 2. 원인 분석
- **근본 원인**: 입력 검증 로직 부재
- **기존 코드 문제점**:
```java
public void saveSubscription(PushSubscriptionDto subscriptionDto, String memberId) {
    try {
        String subscriptionJson = objectMapper.writeValueAsString(subscriptionDto);
        redisTemplate.opsForHash().put(REDIS_SUBSCRIPTION_HASH_KEY, memberId, subscriptionJson);
        // 유효성 검증 없이 바로 저장!
    } catch (JsonProcessingException e) {
        log.error("Error saving subscription for member {}", memberId, e);
    }
}
```

## 3. 해결 방법
- **변경 내용**: 구독 정보 유효성 검증 로직 추가

### InvalidSubscriptionException.java
```java
public class InvalidSubscriptionException extends RuntimeException {
    public InvalidSubscriptionException(String message) {
        super(message);
    }
}
```

### WebPushService.java
```java
public void saveSubscription(PushSubscriptionDto subscriptionDto, String memberId) {
    validateSubscription(subscriptionDto);  // 저장 전 검증

    try {
        String subscriptionJson = objectMapper.writeValueAsString(subscriptionDto);
        redisTemplate.opsForHash().put(REDIS_SUBSCRIPTION_HASH_KEY, memberId, subscriptionJson);
        log.info("Subscription saved for member {}: {}", memberId, subscriptionJson);
    } catch (JsonProcessingException e) {
        log.error("Error saving subscription for member {}", memberId, e);
    }
}

private void validateSubscription(PushSubscriptionDto dto) {
    if (dto == null) {
        throw new InvalidSubscriptionException("Subscription cannot be null");
    }

    if (!isValidEndpoint(dto.getEndpoint())) {
        throw new InvalidSubscriptionException("Invalid endpoint URL: " + dto.getEndpoint());
    }

    if (dto.getKeys() == null) {
        throw new InvalidSubscriptionException("Missing encryption keys");
    }

    if (dto.getKeys().getP256dh() == null || dto.getKeys().getP256dh().isBlank()) {
        throw new InvalidSubscriptionException("Missing p256dh key");
    }

    if (dto.getKeys().getAuth() == null || dto.getKeys().getAuth().isBlank()) {
        throw new InvalidSubscriptionException("Missing auth key");
    }
}

private boolean isValidEndpoint(String endpoint) {
    if (endpoint == null || endpoint.isBlank()) {
        return false;
    }

    try {
        java.net.URI uri = java.net.URI.create(endpoint);
        String scheme = uri.getScheme();
        return "https".equalsIgnoreCase(scheme);  // HTTPS만 허용
    } catch (IllegalArgumentException e) {
        return false;
    }
}
```

## 4. 검증 항목

| 검증 | 설명 |
|------|------|
| null 체크 | 구독 객체 null 여부 |
| endpoint URL | HTTPS 프로토콜 여부 |
| keys 객체 | 암호화 키 객체 존재 여부 |
| p256dh 키 | 공개 키 존재 및 비어있지 않음 |
| auth 키 | 인증 키 존재 및 비어있지 않음 |

## 5. 검증
- [x] 잘못된 구독 정보 저장 차단
- [x] 명확한 에러 메시지로 디버깅 용이
- [x] 전송 실패 감소로 리소스 절약
