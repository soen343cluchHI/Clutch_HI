$ErrorActionPreference = "Stop"
$workspacePath = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $workspacePath
try {
    $outputPath = Join-Path $PSScriptRoot "out"
    New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot "src"), (Join-Path $PSScriptRoot "test") -Recurse -Filter "*.java" | ForEach-Object { $_.FullName })
    & javac --release 17 -d $outputPath @sources
    if ($LASTEXITCODE -ne 0) { throw "Java compilation failed" }
    & java -cp $outputPath sugarrush.ArchitectureChecks
    if ($LASTEXITCODE -ne 0) { throw "Architecture checks failed" }
    & java -cp $outputPath sugarrush.RestaurantOwnershipChecks
    if ($LASTEXITCODE -ne 0) { throw "Restaurant ownership checks failed" }
    & java --add-modules jdk.httpserver -cp $outputPath sugarrush.presentation.http.FirebaseLoginChecks
    if ($LASTEXITCODE -ne 0) { throw "Firebase login checks failed" }
} finally {
    Pop-Location
}
