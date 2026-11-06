param(
    [string]$DevEcoPath = 'D:\DevEco Studio',
    [ValidateSet('assembleHap', 'clean')][string]$Task = 'assembleHap'
)
$ErrorActionPreference = 'Stop'
$node = Join-Path $DevEcoPath 'tools\node\node.exe'
$hvigor = Join-Path $DevEcoPath 'tools\hvigor\bin\hvigorw.js'
foreach ($file in @($node, $hvigor)) {
    if (-not (Test-Path -LiteralPath $file)) { throw "Missing DevEco tool: $file" }
}
$oldPath = $env:Path
$oldJava = $env:JAVA_HOME
$oldSdk = $env:DEVECO_SDK_HOME
Push-Location $PSScriptRoot
try {
    $env:Path = "$DevEcoPath\tools\node;$DevEcoPath\tools\ohpm\bin;$oldPath"
    $env:JAVA_HOME = Join-Path $DevEcoPath 'jbr'
    $env:DEVECO_SDK_HOME = Join-Path $DevEcoPath 'sdk'
    & $node $hvigor --mode module -p product=default -p module=entry@default $Task --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Hvigor failed: $LASTEXITCODE" }
} finally {
    Pop-Location
    $env:Path = $oldPath
    $env:JAVA_HOME = $oldJava
    $env:DEVECO_SDK_HOME = $oldSdk
}
