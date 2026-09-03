// 2단계(동시성 문제 재현)에서 채워 넣을 k6 스크립트.
// 지금은 뼈대만 준비 — 실제 실행은 시드 데이터/시나리오가 정해진 뒤 2단계에서 진행합니다.
import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const USER_ID = __ENV.USER_ID || "testuser";
const ETF_CODE = __ENV.ETF_CODE || "069500";

export const options = {
  scenarios: {
    concurrent_buy: {
      executor: "shared-iterations",
      vus: 50, // TODO(2단계): 동시 요청 수 조정
      iterations: 50,
      maxDuration: "30s",
    },
  },
};

export default function () {
  const payload = JSON.stringify({
    userId: USER_ID,
    etfCode: ETF_CODE,
    count: 1,
  });

  const res = http.post(`${BASE_URL}/portfolio`, payload, {
    headers: { "Content-Type": "application/json" },
  });

  check(res, { "status is 204": (r) => r.status === 204 });
}

// TODO(2단계): 테스트 종료 후 GET /portfolio/{userId}로 최종 count를 조회해
// "기대값(1 x 요청 수)" 대비 실제 값이 얼마나 유실됐는지 비교하는 검증 스텝 추가.
