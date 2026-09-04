#!/usr/bin/env bash
set -euo pipefail

BEFORE_COMMIT="5fb2c9f"
BEFORE_PORT=18080
AFTER_PORT=18081
MYSQL_CONTAINER="vin-mysql"
DB="vindb"
NS=(10 50 100 300)
WARMUP=5
REPEAT=20

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BEFORE_DIR="$REPO_ROOT/../VIN-before-bench"

PIDS_TO_KILL=()

cleanup() {
  echo
  echo "정리 중..."
  for pid in "${PIDS_TO_KILL[@]:-}"; do
    [ -n "$pid" ] && powershell -NoProfile -Command "Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue" >/dev/null 2>&1 || true
  done
  docker exec "$MYSQL_CONTAINER" mysql -uroot -proot "$DB" -e "
    DELETE FROM portfolios WHERE user_id LIKE 'bench_n%';
    DELETE FROM users WHERE user_id LIKE 'bench_n%';
    DELETE FROM etfs WHERE symbol LIKE 'BENCH%';
  " >/dev/null 2>&1 || true
  if [ -d "$BEFORE_DIR" ]; then
    git -C "$REPO_ROOT" worktree remove "$BEFORE_DIR" --force >/dev/null 2>&1 || true
  fi
  rm -f "$REPO_ROOT/backend/bench.log" "$REPO_ROOT/backend/bench.log.err"
}
trap cleanup EXIT

kill_by_port() {
  local port=$1
  local pid
  pid=$(powershell -NoProfile -Command "(Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty OwningProcess)" 2>/dev/null | tr -d '\r')
  if [ -n "$pid" ]; then
    powershell -NoProfile -Command "Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue" >/dev/null 2>&1 || true
  fi
}

wait_for_start() {
  local log=$1
  for _ in $(seq 1 40); do
    if grep -q "Started WebApplication" "$log" 2>/dev/null; then return 0; fi
    if grep -qi "APPLICATION FAILED TO START" "$log" 2>/dev/null; then
      echo "!! 앱 기동 실패. 로그: $log"
      tail -n 60 "$log"
      exit 1
    fi
    sleep 3
  done
  echo "!! 앱 기동 타임아웃"
  exit 1
}

echo "[0/5] Docker / MySQL 상태 확인"
if ! docker ps >/dev/null 2>&1; then
  echo "Docker Desktop이 꺼져있습니다. 먼저 켜주세요."
  exit 1
fi
if ! docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
  echo "MySQL 컨테이너($MYSQL_CONTAINER)가 안 떠있어요. 저장소 루트에서 'docker compose up -d' 먼저 실행해주세요."
  exit 1
fi

echo "[1/5] 시드 데이터 생성 (N=${NS[*]})"
docker exec "$MYSQL_CONTAINER" mysql -uroot -proot "$DB" -e "
  DELETE FROM portfolios WHERE user_id LIKE 'bench_n%';
  DELETE FROM users WHERE user_id LIKE 'bench_n%';
  DELETE FROM etfs WHERE symbol LIKE 'BENCH%';
" >/dev/null 2>&1

MAX_N=${NS[-1]}
USER_VALUES=""
for n in "${NS[@]}"; do
  USER_VALUES="$USER_VALUES,('bench_n$n','bench_n$n@example.com','pw',NULL)"
done
docker exec "$MYSQL_CONTAINER" mysql -uroot -proot "$DB" -e "
  INSERT INTO users (user_id, email, password, tendency_index) VALUES ${USER_VALUES#,};
" >/dev/null 2>&1

CHUNK=25
count=0
sql=""
for i in $(seq -w 1 "$MAX_N"); do
  sym="BENCH$i"
  n=$((10#$i))
  sql="$sql INSERT INTO etfs (symbol, benchmark, country, current_price, expense_ratio, fund_manager, ipo_date, long_name, month_change, nav_price, quarter_change, shares_outstanding, week52high, week52low, year_change) VALUES ('$sym','BENCH',0,10000.0,'0.1%','Bench','2020-01-01','Bench ETF $sym',1.0,10000.0,1.0,1000000,12000.0,8000.0,5.0);"
  for n_target in "${NS[@]}"; do
    if [ "$n" -le "$n_target" ]; then
      sql="$sql INSERT INTO portfolios (user_id, symbol, count) VALUES ('bench_n$n_target','$sym',1);"
    fi
  done
  count=$((count+1))
  if [ "$count" -ge "$CHUNK" ]; then
    docker exec "$MYSQL_CONTAINER" mysql -uroot -proot "$DB" -e "$sql" >/dev/null 2>&1
    sql=""
    count=0
  fi
done
if [ -n "$sql" ]; then
  docker exec "$MYSQL_CONTAINER" mysql -uroot -proot "$DB" -e "$sql" >/dev/null 2>&1
fi
echo "  완료"

echo "[2/5] Before(수정 전, $BEFORE_COMMIT) 앱 빌드/기동 - 처음 실행 시 1~2분 걸릴 수 있습니다"
if [ ! -d "$BEFORE_DIR" ]; then
  git -C "$REPO_ROOT" worktree add "$BEFORE_DIR" "$BEFORE_COMMIT" >/dev/null
fi
kill_by_port "$BEFORE_PORT"
BEFORE_LOG="$BEFORE_DIR/backend/bench.log"
(
  cd "$BEFORE_DIR/backend"
  OPENAI_API_KEY=dummy-for-benchmark ./mvnw.cmd -q spring-boot:run -Dspring-boot.run.arguments=--server.port=$BEFORE_PORT > "$BEFORE_LOG" 2>&1 &
)
sleep 3
wait_for_start "$BEFORE_LOG"
echo "  기동 완료 (port $BEFORE_PORT)"

echo "[3/5] Before 측정"
declare -A BEFORE_QUERIES
for n in "${NS[@]}"; do
  before_lines=$(wc -l < "$BEFORE_LOG")
  curl -s -o /dev/null "http://localhost:${BEFORE_PORT}/portfolio/bench_n${n}"
  sleep 0.3
  cnt=$(tail -n +$((before_lines+1)) "$BEFORE_LOG" | grep -c "Hibernate: select" || true)
  BEFORE_QUERIES[$n]=$cnt
done

for i in $(seq 1 "$WARMUP"); do curl -s -o /dev/null "http://localhost:${BEFORE_PORT}/portfolio/bench_n${MAX_N}"; done
BEFORE_TIMES=$(for i in $(seq 1 "$REPEAT"); do curl -s -o /dev/null -w "%{time_total}\n" "http://localhost:${BEFORE_PORT}/portfolio/bench_n${MAX_N}"; done)
BEFORE_AVG=$(echo "$BEFORE_TIMES" | awk '{s+=$1; n++} END{printf "%.4f", s/n}')

kill_by_port "$BEFORE_PORT"

echo "[4/5] After(현재 코드) 앱 빌드/기동"
kill_by_port "$AFTER_PORT"
AFTER_LOG="$REPO_ROOT/backend/bench.log"
(
  cd "$REPO_ROOT/backend"
  OPENAI_API_KEY=dummy-for-benchmark ./mvnw.cmd -q spring-boot:run -Dspring-boot.run.arguments=--server.port=$AFTER_PORT > "$AFTER_LOG" 2>&1 &
)
sleep 3
wait_for_start "$AFTER_LOG"
echo "  기동 완료 (port $AFTER_PORT)"

declare -A AFTER_QUERIES
for n in "${NS[@]}"; do
  before_lines=$(wc -l < "$AFTER_LOG")
  curl -s -o /dev/null "http://localhost:${AFTER_PORT}/portfolio/bench_n${n}"
  sleep 0.3
  cnt=$(tail -n +$((before_lines+1)) "$AFTER_LOG" | grep -c "Hibernate: select" || true)
  AFTER_QUERIES[$n]=$cnt
done

for i in $(seq 1 "$WARMUP"); do curl -s -o /dev/null "http://localhost:${AFTER_PORT}/portfolio/bench_n${MAX_N}"; done
AFTER_TIMES=$(for i in $(seq 1 "$REPEAT"); do curl -s -o /dev/null -w "%{time_total}\n" "http://localhost:${AFTER_PORT}/portfolio/bench_n${MAX_N}"; done)
AFTER_AVG=$(echo "$AFTER_TIMES" | awk '{s+=$1; n++} END{printf "%.4f", s/n}')

kill_by_port "$AFTER_PORT"

echo "[5/5] 완료"
echo
echo "================================================================"
echo " JPA N+1 최적화 벤치마크 결과 (Before: $BEFORE_COMMIT → After: 현재 코드)"
echo "================================================================"
printf " %-12s | %-14s | %-14s | %-10s\n" "종목 수(N)" "Before 쿼리수" "After 쿼리수" "감소율"
echo "----------------------------------------------------------------"
for n in "${NS[@]}"; do
  b=${BEFORE_QUERIES[$n]}
  a=${AFTER_QUERIES[$n]}
  reduction=$(awk -v b="$b" -v a="$a" 'BEGIN{ if (b==0) print "N/A"; else printf "%.0f%%", (1 - a/b) * 100 }')
  printf " %-12s | %-14s | %-14s | %-10s\n" "$n" "$b" "$a" "$reduction"
done
echo "----------------------------------------------------------------"
speedup=$(awk -v b="$BEFORE_AVG" -v a="$AFTER_AVG" 'BEGIN{ if (a==0) print "N/A"; else printf "%.1f배", b/a }')
echo " N=${MAX_N} 평균 응답시간 (워밍업 ${WARMUP}회 제외, ${REPEAT}회 평균)"
printf "   Before: %ss\n" "$BEFORE_AVG"
printf "   After : %ss\n" "$AFTER_AVG"
printf "   개선  : %s\n" "$speedup"
echo "================================================================"
