import SwiftUI
import UIKit
import FirebaseAuth

struct IncomingCallPresentation: View {
    @EnvironmentObject private var calls: IncomingCallCoordinator
    @EnvironmentObject private var media: AgoraCallMediaClient
    @State private var isJoining = false

    var body: some View {
        ZStack {
            if let active = calls.activeSession {
                ActiveCallCard(session: active, media: media) {
                    let completed = media.remoteParticipantJoined
                    media.leave()
                    await calls.endActiveCall(completed: completed)
                }
            } else if let ringing = calls.ringingSession {
                IncomingCallCard(
                    session: ringing,
                    isJoining: isJoining,
                    onAnswer: answer,
                    onDecline: {
                        Task { await calls.declineRingingCall() }
                    }
                )
            }
        }
        .animation(.easeInOut(duration: 0.2), value: calls.ringingSession?.id)
        .animation(.easeInOut(duration: 0.2), value: calls.activeSession?.id)
        .onChange(of: calls.activeSession?.id) { _, activeSessionId in
            if activeSessionId == nil {
                media.leave()
            }
        }
        .alert("Call", isPresented: Binding(
            get: { calls.errorMessage != nil },
            set: { if !$0 { calls.clearError() } }
        )) {
            Button("OK", role: .cancel) { calls.clearError() }
        } message: {
            Text(calls.errorMessage ?? "The call could not be completed.")
        }
    }

    private func answer() {
        guard !isJoining else { return }
        isJoining = true
        Task {
            defer { isJoining = false }
            do {
                let session = try await calls.acceptRingingCall()
                try await media.join(session: session)
            } catch {
                media.leave()
                await calls.endActiveCall()
                calls.reportError(error.localizedDescription)
            }
        }
    }
}

private struct IncomingCallCard: View {
    let session: IncomingCallSession
    let isJoining: Bool
    let onAnswer: () -> Void
    let onDecline: () -> Void

    var body: some View {
        VStack {
            Spacer()
            VStack(spacing: 16) {
                Image(systemName: session.isVideo ? "video.fill" : "phone.fill")
                    .font(.system(size: 34, weight: .semibold))
                    .foregroundStyle(.tint)
                    .accessibilityHidden(true)
                Text(session.title)
                    .font(.headline)
                Text(session.callerName)
                    .font(.title3.weight(.semibold))
                Text("Answer to join this secure call.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                HStack(spacing: 12) {
                    Button("Decline", role: .destructive, action: onDecline)
                        .buttonStyle(.bordered)
                        .disabled(isJoining)
                    Button(isJoining ? "Joining..." : "Answer", action: onAnswer)
                        .buttonStyle(.borderedProminent)
                        .disabled(isJoining)
                }
                .accessibilityElement(children: .contain)
            }
            .padding(24)
            .frame(maxWidth: 420)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(radius: 14)
            .padding(20)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.opacity(0.22).ignoresSafeArea())
    }
}

private struct ActiveCallCard: View {
    let session: IncomingCallSession
    @ObservedObject var media: AgoraCallMediaClient
    let onEnd: () async -> Void

    private var displayName: String {
        let currentUid = Auth.auth().currentUser?.uid
        if session.callerId == currentUid {
            return session.isGroup ? "Group call" : "Calling..."
        }
        return session.callerName
    }

    var body: some View {
        ZStack {
            if session.isVideo {
                AgoraCallVideoCanvases(media: media)
                    .ignoresSafeArea()
            } else {
                LinearGradient(
                    colors: [Color(red: 0.03, green: 0.11, blue: 0.16), Color(red: 0.06, green: 0.26, blue: 0.34)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
            }

            VStack(spacing: 16) {
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(displayName)
                            .font(.title3.weight(.bold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                        Text(media.status)
                            .font(.subheadline)
                            .foregroundStyle(.white.opacity(0.76))
                        Text(session.isVideo ? "VIDEO CALL" : "VOICE CALL")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(Color.cyan.opacity(0.9))
                    }
                    Spacer()
                }
                .padding(14)
                .padding(.trailing, session.isVideo ? 132 : 0)
                .background(.black.opacity(0.48), in: RoundedRectangle(cornerRadius: 18, style: .continuous))

                Spacer()
                if !media.remoteParticipantJoined {
                    VStack(spacing: 8) {
                        ProgressView()
                            .tint(.white)
                        Text("Waiting for the other person...")
                            .font(.footnote)
                            .foregroundStyle(.white.opacity(0.82))
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .background(.black.opacity(0.40), in: Capsule())
                }

                CallControlDock(session: session, media: media, onEnd: onEnd)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 18)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
        .foregroundStyle(.white)
        .ignoresSafeArea(edges: .all)
    }
}

private struct CallControlDock: View {
    let session: IncomingCallSession
    @ObservedObject var media: AgoraCallMediaClient
    let onEnd: () async -> Void

    var body: some View {
        HStack(spacing: 14) {
            CallActionButton(
                icon: media.isAudioMuted ? "mic.slash.fill" : "mic.fill",
                label: media.isAudioMuted ? "Unmute" : "Mute",
                isActive: media.isAudioMuted,
                action: media.toggleMicrophone
            )
            CallActionButton(
                icon: media.isSpeakerEnabled ? "speaker.wave.2.fill" : "speaker.slash.fill",
                label: media.isSpeakerEnabled ? "Speaker" : "Earpiece",
                isActive: media.isSpeakerEnabled,
                action: media.toggleSpeaker
            )
            if session.isVideo {
                CallActionButton(
                    icon: media.isVideoMuted ? "video.slash.fill" : "video.fill",
                    label: media.isVideoMuted ? "Camera on" : "Camera off",
                    isActive: media.isVideoMuted,
                    action: media.toggleCamera
                )
                CallActionButton(
                    icon: "camera.rotate.fill",
                    label: "Flip",
                    action: media.switchCamera
                )
            }
            CallActionButton(
                icon: "phone.down.fill",
                label: "End",
                isDestructive: true,
                action: { Task { await onEnd() } }
            )
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 14)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .stroke(.white.opacity(0.16), lineWidth: 1)
        }
    }
}

private struct CallActionButton: View {
    let icon: String
    let label: String
    var isActive = false
    var isDestructive = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 5) {
                Image(systemName: icon)
                    .font(.system(size: 17, weight: .semibold))
                    .frame(width: 46, height: 46)
                    .background(
                        isActive
                            ? Color.cyan.opacity(0.88)
                            : (isDestructive ? Color.red.opacity(0.92) : Color.white.opacity(0.16)),
                        in: Circle()
                    )
                    .foregroundStyle(isActive ? Color(red: 0.03, green: 0.11, blue: 0.16) : Color.white)
                Text(label)
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.88))
                    .lineLimit(1)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

private struct AgoraCallVideoCanvases: UIViewRepresentable {
    @ObservedObject var media: AgoraCallMediaClient

    func makeUIView(context: Context) -> AgoraCallVideoContainer {
        AgoraCallVideoContainer()
    }

    func updateUIView(_ uiView: AgoraCallVideoContainer, context: Context) {
        media.bindVideoViews(local: uiView.localVideoView, remote: uiView.remoteVideoView)
    }

}

private final class AgoraCallVideoContainer: UIView {
    let remoteVideoView = UIView()
    let localVideoView = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black

        remoteVideoView.translatesAutoresizingMaskIntoConstraints = false
        remoteVideoView.backgroundColor = .black
        addSubview(remoteVideoView)

        localVideoView.translatesAutoresizingMaskIntoConstraints = false
        localVideoView.backgroundColor = .darkGray
        localVideoView.layer.cornerRadius = 16
        localVideoView.layer.masksToBounds = true
        addSubview(localVideoView)

        NSLayoutConstraint.activate([
            remoteVideoView.leadingAnchor.constraint(equalTo: leadingAnchor),
            remoteVideoView.trailingAnchor.constraint(equalTo: trailingAnchor),
            remoteVideoView.topAnchor.constraint(equalTo: topAnchor),
            remoteVideoView.bottomAnchor.constraint(equalTo: bottomAnchor),
            localVideoView.topAnchor.constraint(equalTo: safeAreaLayoutGuide.topAnchor, constant: 80),
            localVideoView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -18),
            localVideoView.widthAnchor.constraint(equalToConstant: 116),
            localVideoView.heightAnchor.constraint(equalToConstant: 164),
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}
