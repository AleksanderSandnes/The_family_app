param(
    [string]$Serial = 'emulator-5554',
    [string]$SdkPath = "$env:LOCALAPPDATA\Android\Sdk"
)

$ErrorActionPreference = 'Stop'
$adbPath = Join-Path $SdkPath 'platform-tools\adb.exe'
if ((& $adbPath -s $Serial shell getprop ro.kernel.qemu).Trim() -ne '1') {
    throw 'Use an isolated emulator for these fictional session fixtures.'
}
$androidRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$env:ANDROID_HOME = $SdkPath
& (Join-Path $androidRoot 'gradlew.bat') -p $androidRoot assembleDebug assembleDebugAndroidTest
if ($LASTEXITCODE) { throw 'Fixture APK build failed.' }
foreach ($apk in @('app\build\outputs\apk\debug\app-debug.apk', 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')) {
    & $adbPath -s $Serial install -r (Join-Path $androidRoot $apk)
    if ($LASTEXITCODE) { throw 'Fixture APK installation failed.' }
}
$fixtureClass = 'com.sandnes.familyapp.data.remote.AndroidSecretStoreInstrumentedTest'
$runner = 'com.sandnes.familyapp.test/androidx.test.runner.AndroidJUnitRunner'
function Invoke-Fixture([string]$TestName, [int]$ExpectedCount) {
    $output = & $adbPath -s $Serial shell am instrument -w -r -e class $TestName $runner
    $output
    if (($output -join "`n") -notmatch "OK \($ExpectedCount tests?\)") {
        throw 'Encrypted auth instrumentation failed.'
    }
}
Invoke-Fixture $fixtureClass 4
Invoke-Fixture "$fixtureClass#aSeedRestartFixture" 1
& $adbPath -s $Serial shell am force-stop com.sandnes.familyapp
if ($LASTEXITCODE) { throw 'App force-stop failed.' }
Invoke-Fixture "$fixtureClass#bRestoreRestartFixture" 1
Write-Output 'Keystore, plaintext migration and process-restart checks passed.'
