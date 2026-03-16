param(
    [string]$AndroidSourceRoot = "app/src/main/java",
    [string]$IOSContractFile = "ios-migration/starter/FirebaseContract.swift"
)

if (-not (Test-Path $AndroidSourceRoot)) {
    throw "Android source root not found: $AndroidSourceRoot"
}
if (-not (Test-Path $IOSContractFile)) {
    throw "iOS contract file not found: $IOSContractFile"
}

function Get-EnumRawValues {
    param(
        [string[]]$Lines,
        [string]$EnumName
    )

    $inside = $false
    $values = New-Object System.Collections.Generic.List[string]

    foreach ($line in $Lines) {
        if (-not $inside -and $line -match "^\s*enum\s+$EnumName\b") {
            $inside = $true
            continue
        }
        if ($inside -and $line -match "^\s*}\s*$") {
            break
        }
        if (-not $inside) { continue }
        if ($line -notmatch "^\s*case\s+") { continue }

        $afterCase = ($line -replace "^\s*case\s+", "").Trim()
        $afterCase = $afterCase -replace "//.*$", ""
        $parts = $afterCase.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ }

        foreach ($part in $parts) {
            if ($part -match '^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*"([^"]+)"') {
                $values.Add($matches[2])
            } elseif ($part -match '^([A-Za-z_][A-Za-z0-9_]*)$') {
                $values.Add($matches[1])
            }
        }
    }

    return $values | Sort-Object -Unique
}

$files = Get-ChildItem $AndroidSourceRoot -Recurse -Filter *.kt

$androidCollections = Select-String -Path $files.FullName -Pattern 'collection\("([^"]+)"\)' -AllMatches |
    ForEach-Object { $_.Matches | ForEach-Object { $_.Groups[1].Value } } |
    Sort-Object -Unique

$androidCollectionGroups = Select-String -Path $files.FullName -Pattern 'collectionGroup\("([^"]+)"\)' -AllMatches |
    ForEach-Object { $_.Matches | ForEach-Object { $_.Groups[1].Value } } |
    Sort-Object -Unique

$androidFunctions = Select-String -Path $files.FullName -Pattern 'FunctionsClient\.call(Map|Raw|Data)?\("([^"]+)"\s*(,|\))|httpsCallable\("([^"]+)"\)' -AllMatches |
    ForEach-Object {
        foreach ($m in $_.Matches) {
            if ($m.Groups[2].Value) { $m.Groups[2].Value }
            elseif ($m.Groups[4].Value) { $m.Groups[4].Value }
        }
    } |
    Sort-Object -Unique

$androidStorageSegments = Select-String -Path $files.FullName -Pattern '\.child\("([^"]+)"\)' -AllMatches |
    ForEach-Object { $_.Matches | ForEach-Object { $_.Groups[1].Value } } |
    ForEach-Object {
        if ($_ -match '^\$') { return }
        ($_ -split '/')[0]
    } |
    Where-Object { $_ -and $_ -notmatch '^\$' } |
    Sort-Object -Unique

$contractLines = Get-Content $IOSContractFile
$iosCollections = Get-EnumRawValues -Lines $contractLines -EnumName "FirestoreCollection"
$iosSubcollections = Get-EnumRawValues -Lines $contractLines -EnumName "FirestoreSubcollection"
$iosCollectionGroups = Get-EnumRawValues -Lines $contractLines -EnumName "FirestoreCollectionGroup"
$iosFunctions = Get-EnumRawValues -Lines $contractLines -EnumName "CallableFunction"
$iosStorageFolders = Get-EnumRawValues -Lines $contractLines -EnumName "StorageFolder"

$iosCollectionUniverse = ($iosCollections + $iosSubcollections) | Sort-Object -Unique

$missingCollectionRefs = $androidCollections | Where-Object { $_ -notin $iosCollectionUniverse }
$missingCollectionGroups = $androidCollectionGroups | Where-Object { $_ -notin $iosCollectionGroups }
$missingFunctions = $androidFunctions | Where-Object { $_ -notin $iosFunctions }
$missingStorageFolders = $androidStorageSegments | Where-Object { $_ -notin $iosStorageFolders }

Write-Output "== Android -> iOS Contract Diff =="
Write-Output "Android collections referenced: $($androidCollections.Count)"
Write-Output "Android collection groups referenced: $($androidCollectionGroups.Count)"
Write-Output "Android callable functions referenced: $($androidFunctions.Count)"
Write-Output "Android storage root folders referenced: $($androidStorageSegments.Count)"
Write-Output ""

Write-Output "Missing collection/subcollection names in iOS contract: $($missingCollectionRefs.Count)"
$missingCollectionRefs | ForEach-Object { Write-Output "  - $_" }
Write-Output ""

Write-Output "Missing collectionGroup names in iOS contract: $($missingCollectionGroups.Count)"
$missingCollectionGroups | ForEach-Object { Write-Output "  - $_" }
Write-Output ""

Write-Output "Missing callable function names in iOS contract: $($missingFunctions.Count)"
$missingFunctions | ForEach-Object { Write-Output "  - $_" }
Write-Output ""

Write-Output "Missing storage folder names in iOS contract: $($missingStorageFolders.Count)"
$missingStorageFolders | ForEach-Object { Write-Output "  - $_" }

if (
    $missingCollectionRefs.Count -eq 0 -and
    $missingCollectionGroups.Count -eq 0 -and
    $missingFunctions.Count -eq 0 -and
    $missingStorageFolders.Count -eq 0
) {
    Write-Output ""
    Write-Output "Contract parity check passed for extracted Android references."
    exit 0
}

exit 2
