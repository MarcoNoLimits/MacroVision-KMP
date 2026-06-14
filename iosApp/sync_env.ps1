# sync_env.ps1
# Synchronizes root .env file keys to iosApp/Configuration/Config.xcconfig

$envFile = "../.env"
$configFile = "Configuration/Config.xcconfig"

$lines = @(
    "// Generated dynamically from root .env file",
    "TEAM_ID=",
    "BUNDLE_ID=com.fitter.app",
    "APP_NAME=MacroVision"
)

if (Test-Path $envFile) {
    Write-Host "Found .env file. Syncing keys..."
    Get-Content $envFile | ForEach-Object {
        $trimmed = $_.Trim()
        if ($trimmed -and -not $trimmed.StartsWith('#') -and $trimmed.Contains('=')) {
            $lines += $trimmed
        }
    }
    $lines | Out-File -FilePath $configFile -Encoding ascii
    Write-Host "Config.xcconfig generated successfully."
} else {
    Write-Warning ".env file not found at $envFile."
}
