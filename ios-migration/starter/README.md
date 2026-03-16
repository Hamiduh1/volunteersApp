# Starter Files Usage

Copy these files into your iOS target:
- `FirebaseContract.swift`
- `CoreModels.swift`

Recommended destination in Xcode:
- `VolunteersAppIOS/Core/Contracts/`
- `VolunteersAppIOS/Core/Models/`

Notes:
- `CoreModels.swift` intentionally keeps fields optional for backward compatibility.
- `ApplicationStatus` decoder normalizes lowercase/uppercase statuses.
- Keep backend keys unchanged while both Android and iOS clients are active.
