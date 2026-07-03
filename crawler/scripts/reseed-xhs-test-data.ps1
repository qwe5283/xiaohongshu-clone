param(
    [switch]$SkipRebuild,
    [switch]$SkipCrawl
)

$ErrorActionPreference = "Stop"
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $OutputEncoding

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$crawlerRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$sqlPath = Join-Path $crawlerRoot "output\xhs_seed_100.sql"
$mediaPath = Join-Path $crawlerRoot "media\xhs-media"
$envPath = Join-Path $repoRoot ".env"
$mysqlPassword = "123456"

if (Test-Path $envPath) {
    $passwordLine = Get-Content $envPath | Where-Object { $_ -match "^MYSQL_PASSWORD=" } | Select-Object -First 1
    if ($passwordLine) {
        $mysqlPassword = $passwordLine.Substring("MYSQL_PASSWORD=".Length)
    }
}

Set-Location $repoRoot

function Invoke-NativeChecked {
    param(
        [Parameter(Mandatory = $true)]
        [scriptblock]$Command
    )
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code $LASTEXITCODE"
    }
}

function Wait-ContainerRunning {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ContainerName
    )
    $deadline = (Get-Date).AddMinutes(3)
    do {
        $running = docker inspect --format='{{.State.Running}}' $ContainerName 2>$null
        if ($running -eq "true") {
            return
        }
        if ((Get-Date) -gt $deadline) {
            throw "Timed out waiting for $ContainerName to run. Last state: $running"
        }
        Start-Sleep -Seconds 2
    } while ($true)
}

function Copy-MediaToFrontend {
    if (-not (Test-Path $mediaPath)) {
        throw "Missing media directory: $mediaPath. Run the crawler without -SkipCrawl first."
    }
    Write-Host "Copying archived media into frontend container..."
    Wait-ContainerRunning -ContainerName "xiaohongshu-frontend"
    Invoke-NativeChecked { docker exec xiaohongshu-frontend sh -c "rm -rf /usr/share/nginx/html/xhs-media && mkdir -p /usr/share/nginx/html/xhs-media" }
    Invoke-NativeChecked { docker cp "$mediaPath\." "xiaohongshu-frontend:/usr/share/nginx/html/xhs-media/" }
}

if (-not $SkipCrawl) {
    Write-Host "Crawling 100 public Xiaohongshu posts, visible comments, users, and media..."
    Invoke-NativeChecked { node .\crawler\scripts\crawl_xhs_public_data.js }
}

Write-Host "Generating seed SQL..."
Invoke-NativeChecked { python .\crawler\scripts\generate_xhs_seed.py }

if (-not $SkipRebuild) {
    Write-Host "Stopping and removing volumes..."
    Invoke-NativeChecked { docker compose down -v }

    Write-Host "Starting services..."
    Invoke-NativeChecked { docker compose up -d --build }
}

Write-Host "Waiting for MySQL to become healthy..."
$deadline = (Get-Date).AddMinutes(5)
do {
    $status = docker inspect --format='{{.State.Health.Status}}' xiaohongshu-mysql 2>$null
    if ($status -eq "healthy") {
        break
    }
    if ((Get-Date) -gt $deadline) {
        throw "Timed out waiting for xiaohongshu-mysql to become healthy. Last status: $status"
    }
    Start-Sleep -Seconds 3
} while ($true)

Write-Host "Importing 100-post Xiaohongshu test seed..."
Get-Content -Raw -Encoding UTF8 $sqlPath | docker exec -i xiaohongshu-mysql mysql -uroot "-p$mysqlPassword" --default-character-set=utf8mb4 xiaohongshu
if ($LASTEXITCODE -ne 0) {
    throw "MySQL import failed with exit code $LASTEXITCODE"
}

Copy-MediaToFrontend

Write-Host "Verifying seeded counts..."
docker exec xiaohongshu-mysql mysql -uroot "-p$mysqlPassword" --default-character-set=utf8mb4 -D xiaohongshu -e "
SELECT
  (SELECT COUNT(*) FROM sys_user WHERE id BETWEEN 1001 AND 9999) AS seeded_users,
  (SELECT COUNT(*) FROM post WHERE id BETWEEN 1001 AND 9999) AS seeded_posts,
  (SELECT COUNT(*) FROM post_image WHERE post_id BETWEEN 1001 AND 9999) AS seeded_images,
  (SELECT COUNT(*) FROM comment WHERE post_id BETWEEN 1001 AND 9999) AS seeded_comments,
  (SELECT COUNT(*) FROM user_action WHERE target_id BETWEEN 1001 AND 9999) AS seeded_actions,
  (SELECT COUNT(*) FROM user_follow WHERE user_id BETWEEN 1001 AND 9999) AS seeded_follows,
  (SELECT COUNT(*) FROM sys_notification WHERE id BETWEEN 1001 AND 9999) AS seeded_notifications;
"
if ($LASTEXITCODE -ne 0) {
    throw "MySQL verification failed with exit code $LASTEXITCODE"
}

Write-Host "Done."
