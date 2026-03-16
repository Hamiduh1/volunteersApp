# Starter Files Usage

Copy these files into your iOS target:
- `FirebaseContract.swift`
- `CoreModels.swift`
- `Core/Services/AuthService.swift`
- `Core/Services/SessionManager.swift`
- `Core/Services/FirestoreService.swift`
- `Core/Services/FunctionsService.swift`
- `Core/Repositories/EventsRepository.swift`
- `Core/Repositories/JobsRepository.swift`
- `App/ContentViewTemplate.swift` (rename to `ContentView.swift`)
- `App/VolunteersAppiOSAppTemplate.swift` (merge into your app entry file)
- `Features/Auth/LoginView.swift`
- `Features/Auth/LoginViewModel.swift`
- `Features/Root/AppRouterView.swift`
- `Features/Volunteer/VolunteerHomeTabView.swift`
- `Features/Volunteer/Events/EventsListView.swift`
- `Features/Volunteer/Events/EventsListViewModel.swift`
- `Features/Volunteer/Jobs/JobsListView.swift`
- `Features/Volunteer/Jobs/JobsListViewModel.swift`

Recommended destination in Xcode:
- `VolunteersAppIOS/Core/Contracts/`
- `VolunteersAppIOS/Core/Models/`
- `VolunteersAppIOS/Core/Services/`
- `VolunteersAppIOS/Core/Repositories/`
- `VolunteersAppIOS/App/`
- `VolunteersAppIOS/Features/...`

Notes:
- `CoreModels.swift` intentionally keeps fields optional for backward compatibility.
- `ApplicationStatus` decoder normalizes lowercase/uppercase statuses.
- Keep backend keys unchanged while both Android and iOS clients are active.
