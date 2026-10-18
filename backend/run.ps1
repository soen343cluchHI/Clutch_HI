param([int]$Port = 8080)

$ErrorActionPreference = "Stop"

$projectPath = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$sourcePath = Join-Path $PSScriptRoot "src"
$outputPath = Join-Path $PSScriptRoot "out"

Push-Location $projectPath

try {
    New-Item -ItemType Directory -Force -Path $outputPath |
        Out-Null

    $sources = @(
        Get-ChildItem -Path $sourcePath -Recurse -Filter "*.java" |
            ForEach-Object { $_.FullName }
    )

    & javac --release 17 --add-modules jdk.httpserver `
        -d $outputPath @sources

    if ($LASTEXITCODE -ne 0) {
        throw "Java compilation failed."
    }

    & java --add-modules jdk.httpserver `
        -cp $outputPath `
        sugarrush.presentation.http.ApiServer `
        "backend/config/live.properties" $Port
}
finally {
    Pop-Location
}
