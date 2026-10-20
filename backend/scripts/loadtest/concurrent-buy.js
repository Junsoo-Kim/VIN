import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const USER_ID = __ENV.USER_ID || "testuser";
const ETF_CODE = __ENV.ETF_CODE || "069500";
const PASSWORD = __ENV.PASSWORD || "testpassword";
const ITERATIONS = 50;

export const options = {
  scenarios: {
    concurrent_buy: {
      executor: "shared-iterations",
      vus: 50, // TODO(2단계): 동시 요청 수 조정
      iterations: ITERATIONS,
      maxDuration: "30s",
    },
  },
  thresholds: {
    checks: ["rate==1"],
  },
};

function authHeaders(session) {
  return { "Content-Type": "application/json", Cookie: `VIN_SESSION=${session}` };
}

function currentCount(session) {
  const res = http.get(`${BASE_URL}/portfolio/${USER_ID}`, { headers: authHeaders(session) });
  check(res, { "portfolio status is 200": (r) => r.status === 200 });
  const item = (res.json() || []).find((p) => p.etfCode === ETF_CODE);
  return item ? item.count : 0;
}

export function setup() {
  const res = http.post(
    `${BASE_URL}/user/signin`,
    JSON.stringify({ userId: USER_ID, password: PASSWORD, currentCookie: "" }),
    { headers: { "Content-Type": "application/json" } },
  );
  check(res, { "signin status is 201": (r) => r.status === 201 });
  const cookie = res.cookies.VIN_SESSION;
  const session = cookie && cookie.length > 0 ? cookie[0].value : "";
  return { session, initialCount: currentCount(session) };
}

export default function (data) {
  const payload = JSON.stringify({
    userId: USER_ID,
    etfCode: ETF_CODE,
    count: 1,
  });

  const res = http.post(`${BASE_URL}/portfolio`, payload, { headers: authHeaders(data.session) });

  check(res, { "status is 204": (r) => r.status === 204 });
}

export function teardown(data) {
  const finalCount = currentCount(data.session);
  const lost = data.initialCount + ITERATIONS - finalCount;
  console.log(`initial=${data.initialCount}, final=${finalCount}, expected=${data.initialCount + ITERATIONS}, lost=${lost}`);
  check(lost, { "no lost updates": (n) => n === 0 });
}
