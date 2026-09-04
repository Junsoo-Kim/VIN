$Port            = 18090
$MysqlContainer  = "vin-mysql"
$KafkaContainer  = "vin-kafka"
$Db              = "vindb"
$ConcurrentBuys  = 150
$PoisonCount     = 5
$DlqTimeoutSec   = 150

$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = Resolve-Path (Join-Path $ScriptDir "..\..")
$LogFile    = Join-Path $BackendDir "kafka-bench-app.log"

$BenchUser  = "kbench_user"
$EtfPrefix  = "KBENCH"

function Invoke-Sql {
    param([string]$Sql)
    docker exec -e MYSQL_PWD=root $MysqlContainer mysql -uroot $Db -e $Sql
}

function Invoke-SqlRows {
    param([string]$Sql)
    docker exec -e MYSQL_PWD=root $MysqlContainer mysql -uroot -N $Db -e $Sql
}

function Get-SqlScalar {
    param([string]$Sql)
    $out = Invoke-SqlRows $Sql
    $line = ($out | Select-Object -Last 1)
    return $line
}

function Get-PidOnPort {
    param([int]$TargetPort)
    $conn = Get-NetTCPConnection -LocalPort $TargetPort -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($conn) { return $conn.OwningProcess }
    return $null
}

$AppStartedByUs = $false

function Stop-BenchApp {
    if ($AppStartedByUs) {
        $procId = Get-PidOnPort -TargetPort $Port
        if ($procId) {
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
    }
}

function Remove-BenchData {
    Invoke-Sql "DELETE FROM portfolios WHERE user_id = '$BenchUser';"
    Invoke-Sql "DELETE FROM portfolio_history WHERE user_id = '$BenchUser';"
    Invoke-Sql "DELETE FROM portfolio_sync_failures WHERE user_id LIKE 'kbench_poison_%';"
    Invoke-Sql "DELETE FROM users WHERE user_id = '$BenchUser';"
    Invoke-Sql "DELETE FROM etfs WHERE symbol LIKE '${EtfPrefix}%';"
}

try {
    Write-Host "[0/6] Docker / 컨테이너 상태 확인"
    docker ps > $null
    $running = docker ps --format "{{.Names}}"
    foreach ($c in @($MysqlContainer, $KafkaContainer, "vin-redis")) {
        if ($running -notcontains $c) {
            Write-Host "컨테이너 '$c' 가 안 떠있습니다. 저장소 루트에서 'docker compose up -d' 먼저 실행해주세요."
            exit 1
        }
    }

    Write-Host "[1/6] 시드 데이터 생성 (유저 1명 + 종목 $ConcurrentBuys 개)"
    Remove-BenchData
    Invoke-Sql "INSERT INTO users (user_id, email, password, tendency_index) VALUES ('$BenchUser','$BenchUser@example.com','pw',NULL);"
    $chunkSize = 25
    $sql = ""
    $count = 0
    for ($i = 1; $i -le $ConcurrentBuys; $i++) {
        $sym = "{0}{1:D3}" -f $EtfPrefix, $i
        $sql += "INSERT INTO etfs (symbol, benchmark, country, current_price, expense_ratio, fund_manager, ipo_date, long_name, month_change, nav_price, quarter_change, shares_outstanding, week52high, week52low, year_change) VALUES ('$sym','BENCH',0,10000.0,'0.1%','Bench','2020-01-01','Kafka Bench ETF $sym',1.0,10000.0,1.0,1000000,12000.0,8000.0,5.0);"
        $count++
        if ($count -ge $chunkSize) {
            Invoke-Sql $sql
            $sql = ""
            $count = 0
        }
    }
    if ($sql -ne "") { Invoke-Sql $sql }
    $WarmupEtf = "${EtfPrefix}WARM"
    Invoke-Sql "INSERT INTO etfs (symbol, benchmark, country, current_price, expense_ratio, fund_manager, ipo_date, long_name, month_change, nav_price, quarter_change, shares_outstanding, week52high, week52low, year_change) VALUES ('$WarmupEtf','BENCH',0,10000.0,'0.1%','Bench','2020-01-01','Kafka Bench Warmup ETF',1.0,10000.0,1.0,1000000,12000.0,8000.0,5.0);"
    Write-Host "  완료"

    Write-Host "[2/6] 앱 빌드/기동 (포트 $Port) - 처음이면 시간이 좀 걸릴 수 있습니다"
    if (Test-Path $LogFile) { Remove-Item $LogFile -Force }
    $env:OPENAI_API_KEY = "dummy-for-benchmark"
    $RunId = Get-Date -Format "yyyyMMddHHmmss"
    $GroupId = "vin-bench-$RunId"
    $env:SPRING_KAFKA_CONSUMER_GROUP_ID = $GroupId
    $env:SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET = "latest"
    Start-Process -FilePath (Join-Path $BackendDir "mvnw.cmd") `
        -ArgumentList "-q", "spring-boot:run", "-Dspring-boot.run.arguments=--server.port=$Port" `
        -WorkingDirectory $BackendDir `
        -RedirectStandardOutput $LogFile `
        -RedirectStandardError "$LogFile.err" `
        -WindowStyle Hidden
    $AppStartedByUs = $true

    $started = $false
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Seconds 3
        if (Test-Path $LogFile) {
            $content = Get-Content $LogFile -Raw -ErrorAction SilentlyContinue
            if ($content -and $content -match "Started WebApplication") { $started = $true; break }
            if ($content -and $content -match "APPLICATION FAILED TO START") {
                Write-Host "!! 앱 기동 실패"
                Get-Content $LogFile -Tail 60
                exit 1
            }
        }
    }
    if (-not $started) { Write-Host "!! 앱 기동 타임아웃"; exit 1 }
    Write-Host "  기동 완료"

    Write-Host "  Kafka 컨슈머 그룹 배정 대기..."
    Start-Sleep -Seconds 8

    Add-Type -AssemblyName System.Net.Http

    Write-Host "  워밍업 5회..."
    $warmupClient = New-Object System.Net.Http.HttpClient
    $warmupClient.Timeout = [TimeSpan]::FromSeconds(30)
    $warmupUrl = "http://localhost:$Port/portfolio"
    $warmupJson = '{"userId":"' + $BenchUser + '","etfCode":"' + $WarmupEtf + '","count":1}'
    for ($i = 0; $i -lt 5; $i++) {
        $wc = New-Object System.Net.Http.StringContent($warmupJson, [System.Text.Encoding]::UTF8, "application/json")
        $warmupClient.PostAsync($warmupUrl, $wc).Result.Dispose()
    }
    $warmupClient.Dispose()
    Invoke-Sql "DELETE FROM portfolios WHERE user_id='$BenchUser' AND symbol='$WarmupEtf';"
    Invoke-Sql "DELETE FROM portfolio_history WHERE user_id='$BenchUser' AND etf_code='$WarmupEtf';"

    Write-Host "[3/6] Part A: 서로 다른 종목 ${ConcurrentBuys}개를 동시 매수 (락 경합 없이 순수 응답시간 측정)"

    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(30)
    $url = "http://localhost:$Port/portfolio"

    $tasks = New-Object 'System.Collections.Generic.List[System.Threading.Tasks.Task[System.Net.Http.HttpResponseMessage]]'
    $stopwatches = New-Object 'System.Collections.Generic.List[System.Diagnostics.Stopwatch]'

    for ($i = 1; $i -le $ConcurrentBuys; $i++) {
        $sym = "{0}{1:D3}" -f $EtfPrefix, $i
        $jsonBody = '{"userId":"' + $BenchUser + '","etfCode":"' + $sym + '","count":1}'
        $content = New-Object System.Net.Http.StringContent($jsonBody, [System.Text.Encoding]::UTF8, "application/json")
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $task = $client.PostAsync($url, $content)
        $tasks.Add($task)
        $stopwatches.Add($sw)
    }
    [System.Threading.Tasks.Task]::WaitAll($tasks.ToArray())

    $latenciesMs = New-Object 'System.Collections.Generic.List[double]'
    $successCount = 0
    for ($i = 0; $i -lt $tasks.Count; $i++) {
        $stopwatches[$i].Stop()
        $latenciesMs.Add($stopwatches[$i].Elapsed.TotalMilliseconds)
        if ($tasks[$i].Result.IsSuccessStatusCode) { $successCount++ }
        $tasks[$i].Result.Dispose()
    }
    $client.Dispose()

    $sorted = $latenciesMs | Sort-Object
    $avgMs = ($sorted | Measure-Object -Average).Average
    $p95Index = [Math]::Min($sorted.Count - 1, [Math]::Ceiling($sorted.Count * 0.95) - 1)
    $p95Ms = $sorted[$p95Index]
    $maxMs = ($sorted | Measure-Object -Maximum).Maximum

    Write-Host "  매수 요청 응답 성공: $successCount / $ConcurrentBuys"
    Write-Host "  요청 응답시간 - 평균: $([Math]::Round($avgMs,1))ms / p95: $([Math]::Round($p95Ms,1))ms / 최대: $([Math]::Round($maxMs,1))ms"

    Write-Host "[4/6] 이벤트 처리(이력 적재) 확인 - 최대 60초 대기"
    $historyCount = 0
    $waitedSec = 0
    while ($waitedSec -lt 60) {
        $out = Get-SqlScalar "SELECT COUNT(*) FROM portfolio_history WHERE user_id='$BenchUser';"
        if ($out -match '\d+') { $historyCount = [int]$Matches[0] }
        if ($historyCount -ge $ConcurrentBuys) { break }
        Start-Sleep -Milliseconds 500
        $waitedSec += 0.5
    }
    $portfolioRowCount = 0
    $prOut = Get-SqlScalar "SELECT COUNT(*) FROM portfolios WHERE user_id='$BenchUser';"
    if ($prOut -match '\d+') { $portfolioRowCount = [int]$Matches[0] }

    $lagLine = Get-SqlScalar "SELECT CONCAT(COUNT(*), '|', COALESCE(AVG(TIMESTAMPDIFF(MICROSECOND, occurred_at, recorded_at)),0)/1000, '|', COALESCE(MAX(TIMESTAMPDIFF(MICROSECOND, occurred_at, recorded_at)),0)/1000) FROM portfolio_history WHERE user_id='$BenchUser';"
    $lagParts = $lagLine -split '\|'
    $avgLagMs = 0.0
    $maxLagMs = 0.0
    if ($lagParts.Count -ge 3) {
        [double]::TryParse($lagParts[1], [ref]$avgLagMs) | Out-Null
        [double]::TryParse($lagParts[2], [ref]$maxLagMs) | Out-Null
    }

    Write-Host "[5/6] Part B: 실패 이벤트 ${PoisonCount}건 주입 (재시도 -> DLT -> 보상 레코드)"
    Write-Host "  (참고: portfolio-events 토픽이 단일 파티션이라 메시지가 순서대로 처리됩니다."
    Write-Host "   앞 메시지의 재시도가 끝나야 다음 메시지 처리가 시작되므로 다소 시간이 걸립니다.)"
    $sentAt = @{}
    for ($i = 1; $i -le $PoisonCount; $i++) {
        $uid = "kbench_poison_$i"
        $poisonJson = '{"userId":"' + $uid + '","etfCode":"' + $EtfPrefix + '01","changeType":"BUY","count":null,"occurredAt":"2026-01-01T00:00:00Z"}'
        $sentAt[$uid] = Get-Date
        $poisonJson | docker exec -i $KafkaContainer /opt/kafka/bin/kafka-console-producer.sh --broker-list localhost:9092 --topic portfolio-events | Out-Null
    }

    $recovered = @{}
    $waited = 0
    while ($waited -lt $DlqTimeoutSec -and $recovered.Count -lt $PoisonCount) {
        Start-Sleep -Seconds 3
        $waited += 3
        $rows = Invoke-SqlRows "SELECT user_id FROM portfolio_sync_failures WHERE user_id LIKE 'kbench_poison_%';"
        foreach ($line in $rows) {
            if ([string]::IsNullOrWhiteSpace($line)) { continue }
            $uid = $line.Trim()
            if (-not $recovered.ContainsKey($uid)) {
                $recovered[$uid] = $waited
            }
        }
    }

    Write-Host "[6/6] 완료"
    Write-Host ""
    Write-Host "================================================================"
    Write-Host " Kafka 비동기 이벤트 처리 + 보상 트랜잭션(DLT) 벤치마크 결과"
    Write-Host "================================================================"
    Write-Host " [Part A] 서로 다른 종목 ${ConcurrentBuys}개 동시 매수 (락 경합 없음)"
    Write-Host "   - 매수 응답 성공        : $successCount / $ConcurrentBuys"
    Write-Host "   - 매수 응답시간(HTTP)   : 평균 $([Math]::Round($avgMs,1))ms / p95 $([Math]::Round($p95Ms,1))ms / 최대 $([Math]::Round($maxMs,1))ms"
    Write-Host "   - 포트폴리오 반영 건수  : $portfolioRowCount / $ConcurrentBuys"
    Write-Host "   - 이력 적재 건수        : $historyCount / $ConcurrentBuys  (유실률 $([Math]::Round((1 - [double]$historyCount/$ConcurrentBuys)*100,1))%)"
    Write-Host "   - 이벤트 처리 지연시간  : 평균 $([Math]::Round($avgLagMs,1))ms / 최대 $([Math]::Round($maxLagMs,1))ms  (매수 응답 이후 비동기로 처리된 시간)"
    Write-Host ""
    Write-Host " [Part B] 실패 이벤트 ${PoisonCount}건 -> 보상 트랜잭션"
    Write-Host "   - 보상 레코드로 복구    : $($recovered.Count) / $PoisonCount"
    foreach ($uid in $sentAt.Keys | Sort-Object) {
        if ($recovered.ContainsKey($uid)) {
            Write-Host "     $uid : 약 $($recovered[$uid])초 만에 재시도 종료 후 DLT 보상 레코드 기록됨"
        } else {
            Write-Host "     $uid : 타임아웃(${DlqTimeoutSec}초) 내 미기록"
        }
    }
    Write-Host "================================================================"

} finally {
    Write-Host ""
    Write-Host "정리 중..."
    Stop-BenchApp
    Start-Sleep -Seconds 1
    try { Remove-BenchData } catch {}
    if (Test-Path $LogFile) { Remove-Item $LogFile -Force -ErrorAction SilentlyContinue }
    if (Test-Path "$LogFile.err") { Remove-Item "$LogFile.err" -Force -ErrorAction SilentlyContinue }
}
