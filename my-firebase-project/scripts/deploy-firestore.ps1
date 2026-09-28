# Deploy Firestore security rules and indexes to volunteersapp-968b2.
# Run from repo root: .\my-firebase-project\scripts\deploy-firestore.ps1
Set-Location $PSScriptRoot\..
firebase deploy --only firestore:rules,firestore:indexes

# Optional bulk reset (requires firebase login or GOOGLE_APPLICATION_CREDENTIALS):
#   cd my-firebase-project\my-firebase-functions
#   node ..\scripts\clear-chat-call-history.cjs
#   node ..\scripts\clear-chat-call-history.cjs --dry-run
