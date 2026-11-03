$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Push-Location $PSScriptRoot
try {
    if (-not [Environment]::Is64BitOperatingSystem -or $env:OS -ne 'Windows_NT') { throw 'Build requires Windows x64.' }
    & mvn --batch-mode --no-transfer-progress clean package dependency:copy-dependencies '-DincludeScope=runtime' '-DoutputDirectory=target/lib'
    if ($LASTEXITCODE -ne 0) { throw 'Maven build/tests failed.' }
    $build = Join-Path $PSScriptRoot 'target/windows'
    $inputDir = Join-Path $build 'input'
    New-Item -ItemType Directory -Path $inputDir -Force | Out-Null
    Copy-Item 'target/offline-toolbox-0.1.0-SNAPSHOT.jar' $inputDir
    Copy-Item 'target/lib' $inputDir -Recurse
    & "$env:JAVA_HOME/bin/jpackage.exe" --type app-image --input $inputDir `
        --main-jar offline-toolbox-0.1.0-SNAPSHOT.jar --main-class local.tender.App `
        --name TenderToolbox --app-version 1.0.3 --dest "$build/app" `
        --add-modules java.desktop,java.logging,java.xml,java.naming,java.sql,java.management,jdk.crypto.ec,jdk.charsets
    if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }
    $app = Join-Path $build 'app/TenderToolbox'
    # Verify the actual portable executable, including the bundled runtime and templates.
    $verify = Join-Path $build 'verification'
    $process = Start-Process -FilePath "$app/TenderToolbox.exe" -ArgumentList @('--verify-demo', "`"$verify`"") -PassThru
    if (-not $process.WaitForExit(180000)) { $process.Kill(); throw 'Packaged demo verification timed out.' }
    if ($process.ExitCode -ne 0) { throw "Packaged demo verification failed: $($process.ExitCode)" }
    if (@(Get-ChildItem $verify -Directory).Count -ne 13) { throw 'Expected all 13 packaged demos.' }
    Copy-Item 'WINDOWS使用说明.txt' $app
    $release = Join-Path $PSScriptRoot 'target/release'
    New-Item -ItemType Directory -Path $release -Force | Out-Null
    $archive = Join-Path $release 'TenderToolbox-Windows-x64.zip'
    Compress-Archive -Path $app -DestinationPath $archive
    $hash = (Get-FileHash $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  TenderToolbox-Windows-x64.zip" | Set-Content "$archive.sha256" -Encoding ascii
    Write-Host "Portable Windows package: $archive"
} finally { Pop-Location }
