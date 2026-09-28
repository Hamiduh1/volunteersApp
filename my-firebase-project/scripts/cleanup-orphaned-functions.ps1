param(
    [switch]$DeployAfter,
    # List matching names only; do not call firebase delete.
    [switch]$WhatIf
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$myFirebaseDir = Split-Path -Parent $scriptDir
# firebase.json and .firebaserc live at the Android repo root (parent of my-firebase-project).
$repoRoot = Split-Path -Parent $myFirebaseDir

Push-Location $repoRoot
try {
    <#
      These names exist in the Firebase project (e.g. us-central1) but are not exported from
      my-firebase-project/my-firebase-functions/src (current source).

      Resolve EITHER by:
      1) Deleting them from the cloud (this script) when they are obsolete / renamed, then deploy; OR
      2) Re-implementing or aliasing them in index.ts / modules if any client still calls them.

      Do NOT delete names that production apps still invoke via getHttpsCallable.
      Review: firebase functions:list --region us-central1
    #>
    $functionsToDelete = @(
        "adminBackfillMindLoomCommentsCount",
        "adminListDepositRequests",
        "createOrganizerWalletTransfer",
        "getConversationCallAvailability",
        "getCurrentUserPrivateFlags",
        "listRecipientPayoutMethods",
        "onCallSessionUpdated",
        "onChatCallLogWrite",
        "onChatMessageCreatedNotifyRecipients",
        "onWalletTransferPayoutStatusChanged",
        "ownerSaveFeeSettings",
        "ownerSaveSystemConfig",
        "reconcileWalletTransferPayoutStatuses",
        "registerUserPushTokens",
        "releaseMaturedExternalDepositHolds",
        "requestPasswordResetLink"
    )

    if ($WhatIf) {
        Write-Host "Would run from: $repoRoot" -ForegroundColor Cyan
        Write-Host "firebase functions:delete $($functionsToDelete -join ' ') --region us-central1 --force" -ForegroundColor Yellow
        return
    }

    Write-Host "Deleting orphaned functions in us-central1 (project from .firebaserc)..." -ForegroundColor Cyan
    firebase functions:delete $functionsToDelete --region us-central1 --force

    if ($DeployAfter) {
        Write-Host "Deploying functions..." -ForegroundColor Cyan
        firebase deploy --only functions --non-interactive
    }
    else {
        Write-Host "Deletion complete. Deploy when ready: firebase deploy --only functions --non-interactive" -ForegroundColor Green
    }
}
finally {
    Pop-Location
}
