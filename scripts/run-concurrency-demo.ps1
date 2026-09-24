<#
.SYNOPSIS
    Run the reservation concurrency demonstration as a standalone command.

.DESCRIPTION
    Log in to the backend and invoke concurrency-test.py to simulate multiple
    clients competing for the same room and time interval.

.EXAMPLE
    .\scripts\run-concurrency-demo.ps1

.EXAMPLE
    .\scripts\run-concurrency-demo.ps1 -RoomId 2 -Count 10

.EXAMPLE
    .\scripts\run-concurrency-demo.ps1 -BaseUrl http://localhost:8081 -RoomId 2 `
        -Date 2026-09-22 -StartTime 14:00 -EndTime 15:30
#>

[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8081',
    [string]$Username = 'zhangsan',
    [string]$Password = '123456',
    [string]$RoomId = '2',
    [int]$Count = 10,
    [string]$SecondRoomId,
    [string]$Date,
    [string]$StartTime,
    [string]$EndTime,
    [string]$Title,
    [int]$ParticipantCount = 1,
    [string]$Remark,
    [switch]$StartBackendIfMissing
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$testScript = Join-Path $PSScriptRoot 'concurrency-test.py'

if (-not (Test-Path -LiteralPath $testScript)) {
    throw "Concurrency test script was not found: $testScript"
}

if ($Count -le 0) {
    throw 'Count must be greater than zero.'
}

$apiRoot = $BaseUrl.TrimEnd('/')
$roomsUrl = "$apiRoot/api/rooms"

function Get-EndpointStatusCode {
    param([string]$Uri)

    try {
        $response = Invoke-WebRequest -Method Get -Uri $Uri -TimeoutSec 3 -UseBasicParsing
        return [int]$response.StatusCode
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            return [int]$_.Exception.Response.StatusCode
        }
        return 0
    }
}

$backendStatus = Get-EndpointStatusCode -Uri $roomsUrl
$backendReady = $backendStatus -in @(200, 401, 403)
if (-not $backendReady) {
    $serverUri = [Uri]$apiRoot
    $listener = Get-NetTCPConnection -LocalPort $serverUri.Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($listener) {
        throw "Port $($serverUri.Port) is occupied, but the TimeSlot API is not ready (HTTP $backendStatus)."
    }
    if (-not $StartBackendIfMissing) {
        throw "TimeSlot backend is not running at $apiRoot. Start it with .\start-dev.cmd and run this script again."
    }

    $startupCommand = Join-Path $repoRoot 'start-dev.cmd'
    if (-not (Test-Path -LiteralPath $startupCommand)) {
        throw "Startup command was not found: $startupCommand"
    }

    Write-Host 'Backend is not running. Starting the local development environment ...' -ForegroundColor Cyan
    & $startupCommand

    $deadline = (Get-Date).AddMinutes(3)
    do {
        Start-Sleep -Seconds 2
        $backendStatus = Get-EndpointStatusCode -Uri $roomsUrl
        $backendReady = $backendStatus -in @(200, 401, 403)
    } while (-not $backendReady -and (Get-Date) -lt $deadline)

    if (-not $backendReady) {
        throw "TimeSlot backend did not become ready at $apiRoot within 3 minutes (last HTTP status: $backendStatus). Check the backend window and MySQL."
    }
}

Write-Host "Backend ready at $apiRoot" -ForegroundColor Green
$roomLabel = if ($RoomId -eq '2') { 'A302' } else { "room $RoomId" }
$demoDate = if ([string]::IsNullOrWhiteSpace($Date)) { (Get-Date).AddDays(1).ToString('yyyy-MM-dd') } else { $Date }
$demoStart = if ([string]::IsNullOrWhiteSpace($StartTime)) { '10:00' } else { $StartTime }
$demoEnd = if ([string]::IsNullOrWhiteSpace($EndTime)) { '11:00' } else { $EndTime }
Write-Host "Demo target: $roomLabel, $demoDate $demoStart-$demoEnd, $Count concurrent requests" -ForegroundColor Cyan

$loginUrl = "$apiRoot/api/auth/login"
$loginBody = @{
    username = $Username
    password = $Password
} | ConvertTo-Json

Write-Host "Logging in to $loginUrl ..." -ForegroundColor Cyan
try {
    $login = Invoke-RestMethod -Method Post -Uri $loginUrl -ContentType 'application/json' -Body $loginBody
} catch {
    throw "Login failed. Check that the backend is running and the credentials are correct. $($_.Exception.Message)"
}

$token = $login.data.token
if ([string]::IsNullOrWhiteSpace($token)) {
    throw 'The login response did not contain data.token.'
}

$arguments = @(
    $testScript,
    '--base-url', "$apiRoot/api",
    '--token', $token,
    '--room-id', $RoomId,
    '--count', $Count.ToString()
)

$optionalArguments = @()
if (-not [string]::IsNullOrWhiteSpace($SecondRoomId)) {
    $optionalArguments += @('--second-room-id', $SecondRoomId)
}
if (-not [string]::IsNullOrWhiteSpace($Date)) {
    $optionalArguments += @('--date', $Date)
}
if (-not [string]::IsNullOrWhiteSpace($StartTime)) {
    $optionalArguments += @('--start-time', $StartTime)
}
if (-not [string]::IsNullOrWhiteSpace($EndTime)) {
    $optionalArguments += @('--end-time', $EndTime)
}
if (-not [string]::IsNullOrWhiteSpace($Title)) {
    $optionalArguments += @('--title', $Title)
}
if ($ParticipantCount -gt 0) {
    $optionalArguments += @('--participant-count', $ParticipantCount.ToString())
}
if (-not [string]::IsNullOrWhiteSpace($Remark)) {
    $optionalArguments += @('--remark', $Remark)
}
$arguments += $optionalArguments

Write-Host "Starting concurrency demo: room=$RoomId count=$Count" -ForegroundColor Cyan
if (-not [string]::IsNullOrWhiteSpace($SecondRoomId)) {
    Write-Host "Testing a second room in parallel: room=$SecondRoomId" -ForegroundColor Cyan
}

Push-Location $repoRoot
try {
    & python @arguments
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($exitCode -eq 0) {
    Write-Host 'Concurrency demo passed.' -ForegroundColor Green
} else {
    Write-Host "Concurrency demo failed with exit code: $exitCode" -ForegroundColor Red
}

exit $exitCode
