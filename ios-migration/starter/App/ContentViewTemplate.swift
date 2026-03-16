import SwiftUI

struct ContentView: View {
    @EnvironmentObject var session: SessionManager

    var body: some View {
        switch session.state {
        case .loading:
            ProgressView("Loading...")
        case .signedOut:
            Text("Signed out - add Login screen next")
                .padding()
        case .signedIn(let user):
            Text("Signed in as \(user.role.rawValue)")
                .padding()
        case .error(let message):
            Text("Session error: \(message)")
                .padding()
        }
    }
}
