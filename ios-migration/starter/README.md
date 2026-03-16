# Starter Files Usage

Copy these files into your iOS target:
- `FirebaseContract.swift`
- `CoreModels.swift`
- `Core/Services/AuthService.swift`
- `Core/Services/SessionManager.swift`
- `Core/Services/FirestoreService.swift`
- `Core/Services/FunctionsService.swift`
- `App/ContentViewTemplate.swift` (rename to `ContentView.swift`)
- `App/VolunteersAppiOSAppTemplate.swift` (merge into your app entry file)

Recommended destination in Xcode:
- `VolunteersAppIOS/Core/Contracts/`
- `VolunteersAppIOS/Core/Models/`
- `VolunteersAppIOS/Core/Services/`
- `VolunteersAppIOS/App/`

Notes:
- `CoreModels.swift` intentionally keeps fields optional for backward compatibility.
- `ApplicationStatus` decoder normalizes lowercase/uppercase statuses.
- Keep backend keys unchanged while both Android and iOS clients are active.
