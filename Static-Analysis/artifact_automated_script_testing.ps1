param(
    [string]$apkFolder = ".\sample_apks",
    [string]$logFile = ".\pe_results.txt",
    [int]$maxApps = 50
)

$apks = Get-ChildItem "$apkFolder\*.apk" | Sort-Object Name | Select-Object -First $maxApps

foreach ($apk in $apks) {
    Write-Host "Testing $($apk.Name)..."
    & adb logcat -c 2>&1 | Out-Null
    $install = & adb install -r -g $apk.FullName 2>&1
    if ("$install" -notmatch "Success") {
        "$($apk.Name) | INSTALL_FAILED | | | | | | | | | | | | | INSTALL_FAILED" | Out-File $logFile -Append
        Write-Host "  INSTALL FAILED"
        continue
    }
    $pkg = & adb shell pm list packages 2>&1 | Select-String "package:" | Select-Object -Last 1
    $pkg = ($pkg -replace "package:","").Trim()
    Write-Host "  Package: $pkg - waiting 60s..."
    Start-Sleep -Seconds 60
    $dumpsys = & adb shell dumpsys location 2>&1
    $logcat  = & adb logcat -d 2>&1

    $gps  = if ($dumpsys -match "gps")      { "GPS" }      else { "" }
    $wifi = if ($dumpsys -match "wifi")     { "WiFi" }     else { "" }
    $cell = if ($dumpsys -match "network")  { "Cell" }     else { "" }
    $geo  = if ($dumpsys -match "geofence") { "Geofence" } else { "" }
    $channels = ($gps,$wifi,$cell,$geo | Where-Object { $_ }) -join "+"

    $apis = @()
    if ($logcat -match "requestLocationUpdates") { $apis += "reqLocUpd" }
    if ($logcat -match "onLocationChanged")      { $apis += "onLocChanged" }
    if ($logcat -match "getConnectionInfo")      { $apis += "getConnInfo" }
    if ($logcat -match "getScanResults")         { $apis += "getScanResults" }
    if ($logcat -match "getCellLocation")        { $apis += "getCellLoc" }
    if ($logcat -match "getAllCellInfo")          { $apis += "getAllCellInfo" }
    if ($logcat -match "addGeofences")           { $apis += "addGeofences" }
    if ($logcat -match "GnssMeasurement")        { $apis += "GNSS" }
    $api_str = if ($apis.Count -gt 0) { $apis -join "+" } else { "NONE" }

    $pe_fired = if ($logcat -match "PolicyEngine") { "YES" } else { "NO" }
    $spoofed  = if ($dumpsys -match "39\." -and $dumpsys -match "116\.") { "YES" } else { "NO" }
    $bg_loc    = if ($logcat -match "ACCESS_BACKGROUND_LOCATION") { "YES" } else { "NO" }
    $crashed   = if ($logcat -match "FATAL|ANR|crash") { "YES" } else { "NO" }
    $multichan = if (($channels -split "\+").Count -ge 3) { "OUTLIER_CANDIDATE" } else { "NO" }
    $mock_check = if ($logcat -match "isMock|isFromMockProvider") { "YES" } else { "NO" }
    $root_check = if ($logcat -match "su|magisk|root" ) { "YES" } else { "NO" }
    $net_upload = if ($logcat -match "latitude|longitude|lat=|lon=|lng=") { "YES" } else { "NO" }

    $health_connect = if ($logcat -match "HealthConnect|ExerciseRoute|health\.connect") { "YES" } else { "NO" }
    $wear_datalayer = if ($logcat -match "DataClient|MessageClient|putDataItem|WearableListenerService") { "YES" } else { "NO" }
    $gatt           = if ($logcat -match "BluetoothGatt|onCharacteristicChanged|00001819|GATT") { "YES" } else { "NO" }
    $ble_scan       = if ($logcat -match "BluetoothLeScanner|startScan|ScanResult") { "YES" } else { "NO" }
    $wearable_flag  = if ($health_connect -eq "YES" -or $wear_datalayer -eq "YES" -or $gatt -eq "YES") { "WEARABLE_CANDIDATE" } else { "NO" }

    $verdict = if ($crashed -eq "YES") { "CRASHED" }
               elseif ($pe_fired -eq "YES" -or $spoofed -eq "YES") { "DEFEAT" }
               else { "MANUAL_REVIEW" }

    $line = "$($apk.Name) | $pkg | CHANNELS:$channels | APIS:$api_str | PE:$pe_fired | SPOOFED:$spoofed | BG:$bg_loc | MOCK_CHECK:$mock_check | ROOT_CHECK:$root_check | NET_UPLOAD:$net_upload | CRASHED:$crashed | MULTICHAN:$multichan | HC:$health_connect | WEAR:$wear_datalayer | GATT:$gatt | BLE:$ble_scan | WEARABLE:$wearable_flag | $verdict"
    $line | Out-File $logFile -Append
    Write-Host "  CHANNELS:$channels | PE:$pe_fired | SPOOFED:$spoofed | MOCK_CHECK:$mock_check | WEARABLE:$wearable_flag | MULTICHAN:$multichan | $verdict"

    & adb uninstall $pkg 2>&1 | Out-Null
}
Write-Host "DONE. Check $logFile"