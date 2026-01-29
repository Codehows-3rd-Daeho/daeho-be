# k6 부하 테스트 가이드

## 1. k6 설치

### Windows
```powershell
# winget
winget install k6

# choco
choco install k6
```

### macOS
```bash
brew install k6
```

### Linux
```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
    --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D68
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
    | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6
```

### Docker (설치 없이 실행)
```bash
docker run --rm -i --network host grafana/k6 run - <k6/load-test.js
```

---

## 2. 사전 준비

테스트용 계정이 DB에 존재해야 합니다.

```sql
-- 테스트 계정 확인 (이미 있으면 건너뛰기)
SELECT * FROM member WHERE login_id = 'testuser';
```

환경 변수로 계정 정보를 전달합니다:

```bash
k6 run -e LOGIN_ID=실제아이디 -e PASSWORD=실제비밀번호 k6/load-test.js
```

---

## 3. 테스트 실행

### 스모크 테스트 (빠른 기본 확인)
```bash
k6 run k6/load-test.js
```
> smoke 시나리오만 먼저 실행됩니다 (1 VU, 10 iterations)

### 전체 부하 테스트 (스모크 + 평균부하 + 스트레스)
```bash
k6 run k6/load-test.js
```
> 3개 시나리오가 순차적으로 실행됩니다 (총 약 6분)

### 스파이크 테스트 (갑작스러운 트래픽 급증)
```bash
k6 run k6/spike-test.js
```
> 5명 → 200명 급증 → 5명 회복 (총 약 1분 30초)

### 내구성 테스트 (장시간 안정성)
```bash
k6 run k6/soak-test.js
```
> 30명으로 10분간 지속 (메모리 누수, 커넥션 풀 고갈 등 확인)

### 대상 서버 변경
```bash
k6 run -e BASE_URL=http://192.168.1.100:8080 k6/load-test.js
```

---

## 4. 테스트 스크립트 설명

### load-test.js (종합 부하 테스트)
| 시나리오 | VU 수 | 시간 | 목적 |
|---------|-------|------|------|
| smoke | 1 | 즉시 | 기본 동작 확인 |
| average_load | 0→20→20→0 | 2분 | 일반 사용 패턴 |
| stress | 0→50→100→0 | 3분 | 한계 성능 확인 |

**테스트 흐름:**
1. 로그인 → JWT 토큰 획득
2. 이슈 칸반/리스트 조회
3. 회의 리스트/캘린더 조회
4. 알림 조회/미읽음 수 조회
5. 기준 데이터(카테고리/부서/직급) 조회

### spike-test.js (스파이크 테스트)
- 5명 → **200명 급증** → 5명 회복
- `http.batch()`로 4개 API 동시 호출
- 서버의 급격한 부하 대응 능력 확인

### soak-test.js (내구성 테스트)
- 30명으로 **10분간** 지속
- 메모리 누수, DB 커넥션 풀 고갈, GC 문제 등 확인

---

## 5. 성능 기준 (Thresholds)

| 메트릭 | 기준 | 설명 |
|--------|------|------|
| `http_req_duration` p(95) | < 2초 | 95% 요청이 2초 이내 응답 |
| `http_req_failed` | < 5% | 전체 실패율 5% 미만 |
| `errors` | < 10% | 비즈니스 에러율 10% 미만 |
| `login_duration` p(95) | < 3초 | 로그인 응답 시간 |
| `issue_api_duration` p(95) | < 2초 | 이슈 API 응답 시간 |
| `meeting_api_duration` p(95) | < 2초 | 회의 API 응답 시간 |

기준 미달 시 k6가 exit code 99로 종료되어 CI에서 빌드 실패 처리됩니다.

---

## 6. 결과 확인

### 콘솔 출력 (기본)
```
     ✓ 칸반 조회 200
     ✓ 이슈 리스트 200
     ✓ 회의 리스트 200

     checks.....................: 100.00% ✓ 420  ✗ 0
     http_req_duration..........: avg=45ms  p(95)=120ms
     http_reqs..................: 1260    42/s
```

### JSON 리포트
```bash
k6 run --out json=k6/results.json k6/load-test.js
```

### HTML 리포트 (k6-reporter)
```bash
# 결과를 JSON으로 저장 후 변환
k6 run --summary-export=k6/summary.json k6/load-test.js
```

### Grafana + InfluxDB (실시간 대시보드)
```bash
# InfluxDB로 메트릭 전송
k6 run --out influxdb=http://localhost:8086/k6 k6/load-test.js
```

---

## 7. Jenkins 연동

Jenkinsfile에 이미 추가된 `Test` 스테이지 이후 또는 `Verify Deployment` 이후에 실행합니다:

```groovy
stage('Load Test') {
    steps {
        script {
            echo 'Running k6 load tests...'
            sh '''
                k6 run \
                    -e BASE_URL=http://localhost:8080 \
                    -e LOGIN_ID=${LOAD_TEST_USER} \
                    -e PASSWORD=${LOAD_TEST_PW} \
                    --summary-export=k6/summary.json \
                    k6/load-test.js
            '''
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'k6/summary.json', allowEmptyArchive: true
        }
        failure {
            echo 'Load test thresholds not met!'
        }
    }
}
```

> Jenkins 에이전트에 k6가 설치되어 있어야 합니다.
> Docker 기반이라면 `docker run --rm --network host grafana/k6 run -` 형식을 사용하세요.
