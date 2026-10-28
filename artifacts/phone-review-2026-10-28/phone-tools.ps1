$ErrorActionPreference = 'Stop'
$Adb = 'C:\Users\James\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$Serial = '0N14406I221104BA'
$Evidence = 'C:\Users\James\Documents\GitHub\AGAP\artifacts\phone-review-2026-10-28'
function Phone {
    & $Adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" }
}
function Snapshot([string]$Name) {
    Phone shell uiautomator dump /sdcard/agap-review.xml | Out-Null
    Phone pull /sdcard/agap-review.xml "$Evidence\$Name.xml" | Out-Null
    Phone shell screencap -p /sdcard/agap-review.png | Out-Null
    Phone pull /sdcard/agap-review.png "$Evidence\$Name.png" | Out-Null
    [xml]$ui = Get-Content -LiteralPath "$Evidence\$Name.xml" -Raw
    $ui.SelectNodes('//node') | Where-Object { $_.text -or $_.'content-desc' -or $_.class -match 'EditText' } | ForEach-Object {
        [pscustomobject]@{text=$_.text; description=$_.'content-desc'; class=$_.class; bounds=$_.bounds; clickable=$_.clickable; enabled=$_.enabled}
    } | ConvertTo-Json -Compress
}
function AppLogs([string]$Name) {
    $appPid = (Phone shell pidof com.travelingsalesman.agap.debug).Trim()
    Phone logcat -d -b all --pid=$appPid -v threadtime | Set-Content -LiteralPath "$Evidence\$Name.log" -Encoding utf8
    Write-Output "App PID: $appPid"
    Get-Content -LiteralPath "$Evidence\$Name.log" | Where-Object { $_ -match '\s[WEF]\s|FATAL EXCEPTION|ANR|Skipped [0-9]+ frames' -and $_ -notmatch '\t(at |\.\.\.)|Caused by:' } | Select-Object -Last 25
}
