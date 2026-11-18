# Сборка BadgeBridge без Gradle — только aapt2 + javac + d8 + zipalign + apksigner.
# Требуется: JDK 17+, Android SDK (build-tools 34+, platforms/android-34/android.jar).
#
#   .\build.ps1
#   .\build.ps1 -Keystore badge.keystore -StorePass badgebridge
#
# Результат: dist\BadgeBridge-<version>.apk

param(
    [string]$Sdk       = $env:ANDROID_BUILD_TOOLS,
    [string]$Platform  = $env:ANDROID_PLATFORM,
    [string]$Keystore  = "badge.keystore",
    [string]$StorePass = "badgebridge",
    [string]$Alias     = "badgebridge"
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

if (-not $Sdk) {
    $Sdk = "$env:ANDROID_HOME\build-tools\34.0.0"
}
if (-not $Platform) {
    $Platform = "$env:ANDROID_HOME\platforms\android-34\android.jar"
}

$aapt2     = "$Sdk\aapt2.exe"
$zipalign  = "$Sdk\zipalign.exe"
$apksigner = "$Sdk\apksigner.bat"
$d8        = "$Sdk\lib\d8.jar"
$stubs     = "$Sdk\core-lambda-stubs.jar"

foreach ($f in @($aapt2, $zipalign, $apksigner, $d8, $Platform)) {
    if (-not (Test-Path $f)) { throw "Не найдено: $f" }
}

$versionCode = 13
$versionName = "0.4.9"

New-Item -ItemType Directory -Force -Path "build", "dist" | Out-Null

Write-Host "== aapt2 compile =="
& $aapt2 compile --dir res -o build\resources.zip

Write-Host "== aapt2 link =="
& $aapt2 link -o build\base.apk `
    --manifest AndroidManifest.xml `
    build\resources.zip `
    -I $Platform `
    --java build/gen `
    --version-code $versionCode `
    --version-name $versionName

Write-Host "== javac =="
$sources = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac -source 8 -target 8 -encoding utf-8 -classpath $Platform -d build\classes `
    build\gen\local\badgebridge\R.java $sources

Write-Host "== d8 =="
$classes = Get-ChildItem build\classes -Recurse -Filter *.class | ForEach-Object { $_.FullName }
& java -cp $d8 com.android.tools.r8.D8 --release --lib $Platform --lib $stubs `
    --output build\dexout $classes

Write-Host "== pack (resources.arsc должен быть STORED) =="
python pack.py build\base.apk build\dexout\classes.dex build\unsigned.apk

Write-Host "== zipalign =="
& $zipalign -f 4 build\unsigned.apk build\aligned.apk

Write-Host "== apksigner =="
if (-not (Test-Path $Keystore)) {
    keytool -genkey -v -keystore $Keystore -alias $Alias `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $StorePass -keypass $StorePass `
        -dname "CN=BadgeBridge, O=local, C=RU"
}
& $apksigner sign --ks $Keystore --ks-pass pass:$StorePass --ks-key-alias $Alias build\aligned.apk

$out = "dist\BadgeBridge-$versionName.apk"
Copy-Item build\aligned.apk $out
Write-Host "Готово: $out"
& $apksigner verify --print-certs $out
