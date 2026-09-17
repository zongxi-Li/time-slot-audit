# 文件职责：本地开发启动入口，读取根目录 .env.local 并启动前后端服务。
# 接口：调用 java、mvn、npm，以及 Vite/Spring Boot 命令。
[CmdletBinding()]
param(
    [string]$DbUsername = $env:DB_USERNAME,
    [string]$DbPassword = $env:DB_PASSWORD,
    [string]$JwtSecret = $env:JWT_SECRET
)

$ErrorActionPreference = 'Stop'

$repoRoot = $PSScriptRoot
$backendDir = Join-Path $repoRoot 'backend'
$frontendDir = Join-Path $repoRoot 'frontend'
$localEnvPath = Join-Path $repoRoot '.env.local'

function Read-LocalEnv {
    param([string]$Path)

    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $values
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            $name = $Matches[1]
            $value = $Matches[2]
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            $values[$name] = $value
        }
    }

    return $values
}

function Require-Command {
    param([string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Command '$Name' was not found. Install it and add it to PATH."
    }
}

$localEnv = Read-LocalEnv -Path $localEnvPath

if ([string]::IsNullOrWhiteSpace($DbUsername) -and $localEnv.ContainsKey('DB_USERNAME')) {
    $DbUsername = $localEnv['DB_USERNAME']
}

if ([string]::IsNullOrWhiteSpace($DbPassword) -and $localEnv.ContainsKey('DB_PASSWORD')) {
    $DbPassword = $localEnv['DB_PASSWORD']
}

if ([string]::IsNullOrWhiteSpace($JwtSecret) -and $localEnv.ContainsKey('JWT_SECRET')) {
    $JwtSecret = $localEnv['JWT_SECRET']
}

Require-Command 'java'
Require-Command 'mvn'
Require-Command 'npm'

if (-not (Test-Path -LiteralPath (Join-Path $backendDir 'pom.xml'))) {
    throw "Backend project not found: $backendDir\pom.xml"
}

if (-not (Test-Path -LiteralPath (Join-Path $frontendDir 'package.json'))) {
    throw "Frontend project not found: $frontendDir\package.json"
}

if ([string]::IsNullOrWhiteSpace($DbUsername)) {
    $DbUsername = 'root'
}

if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    $securePassword = Read-Host 'Enter the MySQL password' -AsSecureString
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $DbPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
}

if ([string]::IsNullOrWhiteSpace($JwtSecret)) {
    $JwtSecret = 'time-slot-local-secret-change-me'
}

$serverPort = if ($localEnv.ContainsKey('SERVER_PORT')) {
    $localEnv['SERVER_PORT']
} elseif (-not [string]::IsNullOrWhiteSpace($env:SERVER_PORT)) {
    $env:SERVER_PORT
} else {
    '8080'
}

$apiProxyTarget = if ($localEnv.ContainsKey('VITE_API_PROXY_TARGET')) {
    $localEnv['VITE_API_PROXY_TARGET']
} elseif (-not [string]::IsNullOrWhiteSpace($env:VITE_API_PROXY_TARGET)) {
    $env:VITE_API_PROXY_TARGET
} else {
    "http://localhost:$serverPort"
}

$useMock = if ($localEnv.ContainsKey('VITE_USE_MOCK')) {
    $localEnv['VITE_USE_MOCK']
} elseif (-not [string]::IsNullOrWhiteSpace($env:VITE_USE_MOCK)) {
    $env:VITE_USE_MOCK
} else {
    'false'
}

# 环境变量只传给子进程，不会写回仓库文件。
$env:DB_USERNAME = $DbUsername
$env:DB_PASSWORD = $DbPassword
$env:JWT_SECRET = $JwtSecret
$env:SERVER_PORT = $serverPort
$env:VITE_USE_MOCK = $useMock
$env:VITE_API_PROXY_TARGET = $apiProxyTarget

$backendCommand = @"
Set-Location -LiteralPath '$backendDir'
Write-Host 'Starting Spring Boot backend at http://localhost:$serverPort ...' -ForegroundColor Cyan
mvn spring-boot:run
"@

$frontendCommand = @"
Set-Location -LiteralPath '$frontendDir'
if (-not (Test-Path -LiteralPath 'node_modules')) {
    Write-Host 'Installing frontend dependencies ...' -ForegroundColor Cyan
    npm install
}
Write-Host 'Starting Vue frontend at http://localhost:5173 ...' -ForegroundColor Cyan
npm run dev
"@

Start-Process -FilePath 'powershell.exe' `
    -WorkingDirectory $backendDir `
    -ArgumentList @('-NoProfile', '-NoExit', '-ExecutionPolicy', 'Bypass', '-Command', $backendCommand)

Start-Sleep -Seconds 2

Start-Process -FilePath 'powershell.exe' `
    -WorkingDirectory $frontendDir `
    -ArgumentList @('-NoProfile', '-NoExit', '-ExecutionPolicy', 'Bypass', '-Command', $frontendCommand)

Write-Host ''
Write-Host 'Development windows are open.' -ForegroundColor Green
if (Test-Path -LiteralPath $localEnvPath) {
    Write-Host "Local configuration loaded from $localEnvPath"
}
Write-Host "Backend: http://localhost:$serverPort"
Write-Host 'Frontend: http://localhost:5173/login'
Write-Host 'Stop services by closing the two PowerShell windows.'
