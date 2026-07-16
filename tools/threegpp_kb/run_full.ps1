param(
    [string]$Root = "data\3gpp",
    [int]$RetryDelaySeconds = 60
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$corpusRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $Root))
[System.IO.Directory]::CreateDirectory($corpusRoot) | Out-Null
$lockPath = Join-Path $corpusRoot "build.lock"
$lock = $null

try {
    $lock = [System.IO.File]::Open(
        $lockPath,
        [System.IO.FileMode]::OpenOrCreate,
        [System.IO.FileAccess]::ReadWrite,
        [System.IO.FileShare]::None
    )
} catch {
    Write-Error "A 3GPP full build already owns $lockPath"
    exit 4
}

try {
    Set-Location $repoRoot
    $python = (Get-Command python).Source
    $attempt = 0
    while ($true) {
        $attempt++
        Write-Output "supervisor_attempt=$attempt started_at=$([DateTime]::Now.ToString('s'))"
        & $python -u -m tools.threegpp_kb --root $Root build
        $code = $LASTEXITCODE
        Write-Output "supervisor_attempt=$attempt exit_code=$code finished_at=$([DateTime]::Now.ToString('s'))"
        if ($code -eq 0) {
            exit 0
        }
        Start-Sleep -Seconds ([Math]::Max(10, $RetryDelaySeconds))
    }
} finally {
    if ($null -ne $lock) {
        $lock.Dispose()
    }
}

