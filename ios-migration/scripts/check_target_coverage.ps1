param(
    [string]$StarterDir = "ios-migration/starter",
    [Parameter(Mandatory = $true)]
    [string]$TargetSourceDir
)

$readme = Join-Path $StarterDir "README.md"
if (-not (Test-Path $readme)) {
    throw "README not found: $readme"
}

if (-not (Test-Path $TargetSourceDir)) {
    throw "Target source directory not found: $TargetSourceDir"
}

$lines = Get-Content $readme
$start = ($lines | Select-String -SimpleMatch "Copy these files into your iOS target:" | Select-Object -First 1).LineNumber
$end = ($lines | Select-String -SimpleMatch "Recommended destination in Xcode:" | Select-Object -First 1).LineNumber

if (-not $start -or -not $end) {
    throw "Could not parse expected file section from $readme"
}

$expected = $lines[($start)..($end - 2)] |
    Select-String -Pattern '^- `(.+?)`' |
    ForEach-Object { $_.Matches[0].Groups[1].Value } |
    ForEach-Object { ($_ -replace ' \(.*\)$', '').Trim() }

$missingInStarter = New-Object System.Collections.Generic.List[string]
$missingInTarget = New-Object System.Collections.Generic.List[string]

foreach ($rel in $expected) {
    $starterFile = Join-Path $StarterDir $rel
    $targetFile = Join-Path $TargetSourceDir $rel
    if (-not (Test-Path $starterFile)) {
        $missingInStarter.Add($rel)
    }
    if (-not (Test-Path $targetFile)) {
        $missingInTarget.Add($rel)
    }
}

Write-Output "Expected files: $($expected.Count)"
Write-Output "Missing in starter: $($missingInStarter.Count)"
$missingInStarter | ForEach-Object { Write-Output "  - $_" }
Write-Output "Missing in iOS target: $($missingInTarget.Count)"
$missingInTarget | ForEach-Object { Write-Output "  - $_" }

if ($missingInStarter.Count -eq 0 -and $missingInTarget.Count -eq 0) {
    Write-Output "Coverage check passed."
    exit 0
}

exit 2
