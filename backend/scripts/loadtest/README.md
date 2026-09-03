# 부하테스트 (k6)

로컬에 k6를 설치하지 않고 Docker로 실행합니다.

## 사전 준비
- 백엔드 서버가 `localhost:8080`에서 기동 중이어야 합니다.
- `BASE_URL`, `USER_ID`, `ETF_CODE`는 실제 DB에 존재하는 값으로 맞춰야 합니다.

## 실행 방법
```bash
docker run --rm -i \
  -e BASE_URL=http://host.docker.internal:8080 \
  -e USER_ID=testuser \
  -e ETF_CODE=069500 \
  grafana/k6 run - < concurrent-buy.js
```

## 스크립트 목록
- `concurrent-buy.js` : 동일 유저·동일 종목에 대해 `POST /portfolio`를 동시에 여러 건 호출해
  Race Condition(수량 유실) 재현용. **2단계에서 시나리오/검증 로직을 채워 넣습니다.**
