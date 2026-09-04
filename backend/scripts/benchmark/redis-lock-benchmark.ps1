$Levels          = 50, 200, 500
$NoLockPort      = 18095
$LockedPort      = 18096
$MysqlContainer  = "vin-mysql"
$Db              = "vindb"

$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot   = Resolve-Path (Join-Path $ScriptDir "..\..\..")
$BackendDir = Join-Path $RepoRoot "backend"
$NoLockDir  = Join-Path (Split-Path $RepoRoot -Parent) "VIN-nolock-bench"

$BenchUser = "lbench_user"

function Invoke-Sql {
    param([string]$Sql)
    docker exec -e MYSQL_PWD=root $MysqlContainer mysql -uroot $Db -e $Sql
}

function Get-SqlScalar {
    param([string]$Sql)
    $out = docker exec -e MYSQL_PWD=root $MysqlContainer mysql -uroot -N $Db -e $Sql
    return ($out | Select-Object -Last 1)
}

function Stop-AppOnPort {
    param([int]$TargetPort)

    $listeners = Get-NetTCPConnection -LocalPort $TargetPort -State Listen -ErrorAction SilentlyContinue
    foreach ($c in $listeners) {
        Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue
    }

    Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine -match "server\.port=$TargetPort" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

function Start-BenchApp {
    param([string]$WorkDir, [int]$Port, [string]$GroupId, [string]$LogFile)

    if (Test-Path $LogFile) { Remove-Item $LogFile -Force }
    $env:OPENAI_API_KEY = "dummy-for-benchmark"
    $env:SPRING_KAFKA_CONSUMER_GROUP_ID = $GroupId
    $env:SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET = "latest"

    Start-Process -FilePath (Join-Path $WorkDir "mvnw.cmd") `
        -ArgumentList "-q", "spring-boot:run", "-Dspring-boot.run.arguments=--server.port=$Port" `
        -WorkingDirectory $WorkDir `
        -RedirectStandardOutput $LogFile `
        -RedirectStandardError "$LogFile.err" `
        -WindowStyle Hidden

    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Seconds 3
        if (Test-Path $LogFile) {
            $content = Get-Content $LogFile -Raw -ErrorAction SilentlyContinue
            if ($content -and $content -match "Started WebApplication") { return $true }
            if ($content -and $content -match "APPLICATION FAILED TO START") {
                Write-Host "!! 앱 기동 실패 ($LogFile)"
                Get-Content $LogFile -Tail 60
                return $false
            }
        }
    }
    return $false
}

function Remove-NoLockWorktree {
    Stop-AppOnPort -TargetPort $NoLockPort
    Start-Sleep -Seconds 3
    if (Test-Path $NoLockDir) {
        git -C $RepoRoot worktree remove $NoLockDir --force 2>$null
        if (Test-Path $NoLockDir) {
            Remove-Item $NoLockDir -Recurse -Force -ErrorAction SilentlyContinue
            git -C $RepoRoot worktree prune 2>$null
        }
    }
}

function Remove-BenchData {
    Invoke-Sql "DELETE FROM portfolios WHERE user_id = '$BenchUser';"
    Invoke-Sql "DELETE FROM portfolio_history WHERE user_id = '$BenchUser';"
    Invoke-Sql "DELETE FROM users WHERE user_id = '$BenchUser';"
    Invoke-Sql "DELETE FROM etfs WHERE symbol LIKE 'LOCK%';"
}

function Invoke-ConcurrentBuys {
    param([string]$BaseUrl, [string]$UserId, [string]$Symbol, [int]$Count)

    Add-Type -AssemblyName System.Net.Http
    $url = "$BaseUrl/portfolio"
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(180)
    $json = '{"userId":"' + $UserId + '","etfCode":"' + $Symbol + '","count":1}'

    $tasks = New-Object 'System.Collections.Generic.List[System.Threading.Tasks.Task[System.Net.Http.HttpResponseMessage]]'
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    for ($i = 0; $i -lt $Count; $i++) {
        $content = New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, "application/json")
        $tasks.Add($client.PostAsync($url, $content))
    }
    [System.Threading.Tasks.Task]::WaitAll($tasks.ToArray())
    $sw.Stop()

    $success = 0
    foreach ($t in $tasks) {
        if ($t.Result.IsSuccessStatusCode) { $success++ }
        $t.Result.Dispose()
    }
    $client.Dispose()

    [PSCustomObject]@{
        Success = $success
        TotalMs = $sw.Elapsed.TotalMilliseconds
    }
}

$results = @{}
$NoLockStarted = $false
$LockedStarted = $false

try {
    Write-Host "[0/6] Docker / 컨테이너 상태 확인"
    docker ps > $null
    $running = docker ps --format "{{.Names}}"
    foreach ($c in @($MysqlContainer, "vin-redis", "vin-kafka")) {
        if ($running -notcontains $c) {
            Write-Host "컨테이너 '$c' 가 안 떠있습니다. 저장소 루트에서 'docker compose up -d' 먼저 실행해주세요."
            exit 1
        }
    }

    Write-Host "[1/6] 시드 데이터 생성"
    Remove-BenchData
    Invoke-Sql "INSERT INTO users (user_id, email, password, tendency_index) VALUES ('$BenchUser','$BenchUser@example.com','pw',NULL);"
    foreach ($n in $Levels) {
        $sym = "LOCK{0:D3}" -f $n
        Invoke-Sql "INSERT INTO etfs (symbol, benchmark, country, current_price, expense_ratio, fund_manager, ipo_date, long_name, month_change, nav_price, quarter_change, shares_outstanding, week52high, week52low, year_change) VALUES ('$sym','BENCH',0,10000.0,'0.1%','Bench','2020-01-01','Lock Bench ETF $sym',1.0,10000.0,1.0,1000000,12000.0,8000.0,5.0);"
    }
    Write-Host "  완료"

    Write-Host "[2/6] 락 미적용 버전 준비 (git worktree, @DistributedLock 제거)"
    Remove-NoLockWorktree
    git -C $RepoRoot worktree add $NoLockDir HEAD | Out-Null
    $facadeFile = Join-Path $NoLockDir "backend\src\main\java\dsp\backend\service\PortfolioLockFacade.java"
    (Get-Content $facadeFile) | Where-Object { $_ -notmatch '@DistributedLock' } | Set-Content $facadeFile

    Write-Host "[3/6] 락 미적용 버전 빌드/기동 - 처음이면 1~2분 걸릴 수 있습니다"
    Stop-AppOnPort -TargetPort $NoLockPort
    $noLockLog = Join-Path $NoLockDir "backend\bench.log"
    $NoLockStarted = Start-BenchApp -WorkDir (Join-Path $NoLockDir "backend") -Port $NoLockPort -GroupId ("lock-bench-nolock-" + (Get-Date -Format "yyyyMMddHHmmss")) -LogFile $noLockLog
    if (-not $NoLockStarted) { throw "락 미적용 버전 기동 실패" }
    Write-Host "  기동 완료"
    Start-Sleep -Seconds 5

    $results["NoLock"] = @{}
    foreach ($n in $Levels) {
        $sym = "LOCK{0:D3}" -f $n
        Write-Host "  동시 매수 ${n}건 (락 없음, 종목 $sym)"
        $r = Invoke-ConcurrentBuys -BaseUrl "http://localhost:$NoLockPort" -UserId $BenchUser -Symbol $sym -Count $n
        Start-Sleep -Milliseconds 500
        $actual = [int](Get-SqlScalar "SELECT COALESCE(count,0) FROM portfolios WHERE user_id='$BenchUser' AND symbol='$sym';")
        $results["NoLock"][$n] = [PSCustomObject]@{
            Expected = $n
            Actual   = $actual
            Success  = $r.Success
            TotalMs  = $r.TotalMs
        }
    }

    Remove-NoLockWorktree

    Write-Host "[4/6] 락 미적용 데이터 정리 후 현재(락 적용) 코드 기동"
    Invoke-Sql "DELETE FROM portfolios WHERE user_id = '$BenchUser';"
    Invoke-Sql "DELETE FROM portfolio_history WHERE user_id = '$BenchUser';"

    Stop-AppOnPort -TargetPort $LockedPort
    $lockedLog = Join-Path $BackendDir "bench.log"
    $LockedStarted = Start-BenchApp -WorkDir $BackendDir -Port $LockedPort -GroupId ("lock-bench-locked-" + (Get-Date -Format "yyyyMMddHHmmss")) -LogFile $lockedLog
    if (-not $LockedStarted) { throw "락 적용 버전 기동 실패" }
    Write-Host "  기동 완료"
    Start-Sleep -Seconds 5

    Write-Host "[5/6] 동시 매수 재실행 (락 적용)"
    $results["Locked"] = @{}
    foreach ($n in $Levels) {
        $sym = "LOCK{0:D3}" -f $n
        Write-Host "  동시 매수 ${n}건 (락 적용, 종목 $sym)"
        $r = Invoke-ConcurrentBuys -BaseUrl "http://localhost:$LockedPort" -UserId $BenchUser -Symbol $sym -Count $n
        Start-Sleep -Milliseconds 500
        $actual = [int](Get-SqlScalar "SELECT COALESCE(count,0) FROM portfolios WHERE user_id='$BenchUser' AND symbol='$sym';")
        $results["Locked"][$n] = [PSCustomObject]@{
            Expected = $n
            Actual   = $actual
            Success  = $r.Success
            TotalMs  = $r.TotalMs
        }
    }

    Write-Host "[6/6] 완료"
    Write-Host ""
    Write-Host "================================================================"
    Write-Host " Redis 분산 락(Redisson) 벤치마크 결과"
    Write-Host "================================================================"
    Write-Host " (같은 유저·같은 종목에 대한 동시 매수. DB반영/성공응답 - 유실은 '성공 응답을 받았는데 실제로는 반영 안 된' 건수만 센다)"
    Write-Host ""
    Write-Host (" {0,-10} | {1,-28} | {2,-28} | {3,-14}" -f "동시요청", "락 없음", "락 적용", "락 처리시간")
    Write-Host "--------------------------------------------------------------------------------"
    foreach ($n in $Levels) {
        $nl = $results["NoLock"][$n]
        $lk = $results["Locked"][$n]

        $nlLost = $nl.Success - $nl.Actual
        $nlRejected = $nl.Expected - $nl.Success
        $nlCell = "$($nl.Actual)/$($nl.Success) (유실 $nlLost)"
        if ($nlRejected -gt 0) { $nlCell += ", 거부 $nlRejected" }

        $lkLost = $lk.Success - $lk.Actual
        $lkRejected = $lk.Expected - $lk.Success
        $lkCell = "$($lk.Actual)/$($lk.Success) (유실 $lkLost)"
        if ($lkRejected -gt 0) { $lkCell += ", 거부 $lkRejected" }

        $timeCell = "$([Math]::Round($lk.TotalMs,0))ms"
        Write-Host (" {0,-10} | {1,-28} | {2,-28} | {3,-14}" -f $n, $nlCell, $lkCell, $timeCell)
    }
    Write-Host "--------------------------------------------------------------------------------"
    Write-Host " (DB반영/성공응답 형식. 거부는 대기시간 안에 락을 못 잡아 명시적으로 실패 응답한 건수)"
    Write-Host "================================================================"

} finally {
    Write-Host ""
    Write-Host "정리 중..."
    Stop-AppOnPort -TargetPort $LockedPort
    Remove-NoLockWorktree
    try { Remove-BenchData } catch {}
    $lockedLog = Join-Path $BackendDir "bench.log"
    Remove-Item $lockedLog, "$lockedLog.err" -Force -ErrorAction SilentlyContinue
}
