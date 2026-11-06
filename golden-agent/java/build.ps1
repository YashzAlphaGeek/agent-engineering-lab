# Builds the library, runs the self-checks and the demo, and packages dist/golden-agent-<version>.jar.
# Requires a JDK 21 or newer on the PATH. No other tools or downloads are needed.
param(
    [string]$Version = "1.0.0",
    [switch]$SkipDemo
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Remove-Item -Recurse -Force out, dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory out\main, out\test, out\demo, dist | Out-Null

function Compile($sources, $dest, $classpath) {
    $list = Join-Path $env:TEMP ("sources-" + [guid]::NewGuid() + ".txt")
    (Get-ChildItem -Recurse -Path $sources -Filter *.java).FullName | Set-Content -Encoding ascii $list
    $args = @("--release", "21", "-Xlint:all", "-d", $dest)
    if ($classpath) { $args += @("-cp", $classpath) }
    & javac @args "@$list"
    if ($LASTEXITCODE -ne 0) { throw "javac failed for $sources" }
    Remove-Item $list
}

Write-Host "Compiling library..."
Compile "src\main\java" "out\main" $null

Write-Host "Compiling self-checks and demo..."
Compile "src\test\java" "out\test" "out\main"
Compile "src\demo\java" "out\demo" "out\main"

Write-Host "Running self-checks..."
& java -cp "out\main;out\test" golden.agent.check.SelfCheck
if ($LASTEXITCODE -ne 0) { throw "Self-checks failed" }

if (-not $SkipDemo) {
    Write-Host ""
    & java -cp "out\main;out\demo" golden.agent.demo.Demo
}

Write-Host ""
Write-Host "Packaging dist\golden-agent-$Version.jar..."
$manifest = Join-Path $env:TEMP "golden-manifest.txt"
"Automatic-Module-Name: golden.agent`nImplementation-Title: golden-agent`nImplementation-Version: $Version`n" | Set-Content -Encoding ascii $manifest
& jar --create --file "dist\golden-agent-$Version.jar" --manifest $manifest -C out\main .
& jar --create --file "dist\golden-agent-$Version-sources.jar" -C src\main\java .
Write-Host "Done."
