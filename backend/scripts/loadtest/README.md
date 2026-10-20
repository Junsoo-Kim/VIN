# 부하테스트 (k6)

로컬에 k6를 설치하지 않고 Docker로 실행합니다.

## 사전 준비
- 백엔드 서버가 `localhost:8080`에서 기동 중이어야 합니다.
- `BASE_URL`, `USER_ID`, `PASSWORD`, `ETF_CODE`는 실제 DB에 존재하는 값으로 맞춰야 합니다.
- `POST /portfolio`는 로그인 세션이 필요하므로, 스크립트가 `setup()`에서 `/user/signin`으로 로그인한 뒤 `VIN_SESSION` 쿠키로 요청합니다.

## 실행 방법
```bash
docker run --rm -i \
  -e BASE_URL=http://host.docker.internal:8080 \
  -e USER_ID=testuser \
  -e PASSWORD=testpassword \
  -e ETF_CODE=069500 \
  grafana/k6 run - < concurrent-buy.js
```

## 스크립트 목록
- `concurrent-buy.js` : 동일 유저·동일 종목에 대해 `POST /portfolio`를 동시에 여러 건 호출해
  Race Condition(수량 유실) 재현용. 종료 시(`teardown`) `GET /portfolio/{userId}`로 최종 수량을 조회해
  "시작 수량 + 요청 수"와 비교하고, 유실이 있으면 `no lost updates` 체크가 실패합니다.
