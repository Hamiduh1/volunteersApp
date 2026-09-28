import Combine
import FirebaseAuth
import FirebaseFunctions
import Foundation
import UIKit

#if canImport(AgoraRtcKit)
import AgoraRtcKit
#endif

@MainActor
final class AgoraCallMediaClient: NSObject, ObservableObject {
    @Published private(set) var status = "Ready"
    @Published private(set) var remoteParticipantJoined = false
    @Published private(set) var errorMessage: String?
    @Published private(set) var isAudioMuted = false
    @Published private(set) var isVideoMuted = false
    @Published private(set) var isSpeakerEnabled = true
    @Published private(set) var isUsingFrontCamera = true

    static var isAvailable: Bool {
#if canImport(AgoraRtcKit)
        true
#else
        false
#endif
    }

#if canImport(AgoraRtcKit)
    private var engine: AgoraRtcEngineKit?
    private weak var localVideoView: UIView?
    private weak var remoteVideoView: UIView?
    private var remoteUid: UInt?
    private var activeSessionId: String?
    private var activeChannelName: String?
    private var localAgoraUid: Int?
#endif

    /// Binds the native Agora canvases owned by SwiftUI. Local and remote video intentionally use
    /// separate views so the remote feed can never replace the caller's preview.
    func bindVideoViews(local: UIView?, remote: UIView?) {
#if canImport(AgoraRtcKit)
        localVideoView = local
        remoteVideoView = remote
        configureVideoSurfaces()
#endif
    }

    func join(session: IncomingCallSession) async throws {
        let uid = Auth.auth().currentUser?.uid?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !uid.isEmpty else { throw AgoraCallMediaError.signedOut }
        guard !session.agoraChannelName.isEmpty else { throw AgoraCallMediaError.invalidSession }

#if canImport(AgoraRtcKit)
        guard let appId = (Bundle.main.object(forInfoDictionaryKey: "AGORA_APP_ID") as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines), !appId.isEmpty else {
            throw AgoraCallMediaError.missingAppId
        }

        status = "Getting secure call access..."
        let agoraUid = stableAgoraUid(uid)
        let token = try await fetchToken(sessionId: session.id, channelName: session.agoraChannelName, uid: agoraUid)
        let engine = AgoraRtcEngineKit.sharedEngine(withAppId: appId, delegate: self)
        self.engine = engine
        activeSessionId = session.id
        activeChannelName = session.agoraChannelName
        localAgoraUid = agoraUid
        remoteParticipantJoined = false
        isAudioMuted = false
        isVideoMuted = false
        isSpeakerEnabled = true
        isUsingFrontCamera = true
        engine.enableAudio()
        engine.enableLocalAudio(true)
        engine.setDefaultAudioRouteToSpeakerphone(true)
        engine.setEnableSpeakerphone(true)
        if session.isVideo {
            engine.enableVideo()
            engine.enableLocalVideo(true)
            engine.setVideoEncoderConfiguration(
                AgoraVideoEncoderConfiguration(
                    size: AgoraVideoDimension1280x720,
                    frameRate: .fps30,
                    bitrate: AgoraVideoBitrateStandard,
                    orientationMode: .adaptative,
                ),
            )
            configureVideoSurfaces()
            engine.startPreview()
        } else {
            engine.disableVideo()
        }

        let options = AgoraRtcChannelMediaOptions()
        options.channelProfile = .communication
        options.clientRoleType = .broadcaster
        options.publishMicrophoneTrack = true
        options.publishCameraTrack = session.isVideo
        options.autoSubscribeAudio = true
        options.autoSubscribeVideo = session.isVideo
        let result = engine.joinChannel(
            byToken: token,
            channelId: session.agoraChannelName,
            uid: UInt(agoraUid),
            mediaOptions: options
        )
        guard result == 0 else { throw AgoraCallMediaError.joinFailed(result) }
        status = "Connecting..."
#else
        throw AgoraCallMediaError.sdkUnavailable
#endif
    }

    func leave() {
#if canImport(AgoraRtcKit)
        engine?.stopPreview()
        engine?.leaveChannel(nil)
        AgoraRtcEngineKit.destroy()
        engine = nil
        remoteUid = nil
        activeSessionId = nil
        activeChannelName = nil
        localAgoraUid = nil
#endif
        remoteParticipantJoined = false
        status = "Ready"
        isAudioMuted = false
        isVideoMuted = false
        isSpeakerEnabled = true
        isUsingFrontCamera = true
    }

    func clearError() {
        errorMessage = nil
    }

    func toggleMicrophone() {
        let muted = !isAudioMuted
#if canImport(AgoraRtcKit)
        engine?.muteLocalAudioStream(muted)
#endif
        isAudioMuted = muted
    }

    func toggleCamera() {
        let muted = !isVideoMuted
#if canImport(AgoraRtcKit)
        engine?.muteLocalVideoStream(muted)
#endif
        isVideoMuted = muted
    }

    func toggleSpeaker() {
        let enabled = !isSpeakerEnabled
#if canImport(AgoraRtcKit)
        engine?.setEnableSpeakerphone(enabled)
#endif
        isSpeakerEnabled = enabled
    }

    func switchCamera() {
#if canImport(AgoraRtcKit)
        engine?.switchCamera()
#endif
        isUsingFrontCamera.toggle()
    }

#if canImport(AgoraRtcKit)
    private func configureVideoSurfaces() {
        guard let engine else { return }

        if let localVideoView {
            let localCanvas = AgoraRtcVideoCanvas()
            localCanvas.uid = 0
            localCanvas.view = localVideoView
            localCanvas.renderMode = .hidden
            engine.setupLocalVideo(localCanvas)
        }

        if let remoteUid, let remoteVideoView {
            let remoteCanvas = AgoraRtcVideoCanvas()
            remoteCanvas.uid = remoteUid
            remoteCanvas.view = remoteVideoView
            remoteCanvas.renderMode = .hidden
            engine.setupRemoteVideo(remoteCanvas)
        }
    }

    private func renewToken() async {
        guard let engine, let activeSessionId, let activeChannelName, let localAgoraUid else { return }
        do {
            let token = try await fetchToken(
                sessionId: activeSessionId,
                channelName: activeChannelName,
                uid: localAgoraUid,
            )
            guard !token.isEmpty else { return }
            engine.renewToken(token)
        } catch {
            errorMessage = "Could not renew the secure call connection."
        }
    }

    private func fetchToken(sessionId: String, channelName: String, uid: Int) async throws -> String {
        let result = try await Functions.functions(region: "us-central1")
            .httpsCallable("getAgoraRtcToken")
            .call([
                "sessionId": sessionId,
                "channelName": channelName,
                "role": "publisher",
                "uid": uid,
            ])
        let payload = result.data as? [String: Any]
        let tokenRequired = payload?["tokenRequired"] as? Bool ?? true
        let token = (payload?["token"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if tokenRequired && token.isEmpty { throw AgoraCallMediaError.tokenMissing }
        return token
    }
#endif

    private func stableAgoraUid(_ firebaseUid: String) -> Int {
        // Agora UIDs only need to be unique inside the channel; this is stable across iOS launches.
        var hash: UInt32 = 2_166_136_261
        for byte in firebaseUid.utf8 {
            hash ^= UInt32(byte)
            hash &*= 16_777_619
        }
        return Int(hash % 900_000) + 100_000
    }
}

#if canImport(AgoraRtcKit)
extension AgoraCallMediaClient: AgoraRtcEngineDelegate {
    nonisolated func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinChannel channel: String, withUid uid: UInt, elapsed: Int) {
        Task { @MainActor in
            status = "Waiting for the other person..."
        }
    }

    nonisolated func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinedOfUid uid: UInt, elapsed: Int) {
        Task { @MainActor in
            remoteUid = uid
            configureVideoSurfaces()
            remoteParticipantJoined = true
            status = "Connected"
        }
    }

    nonisolated func rtcEngine(_ engine: AgoraRtcEngineKit, tokenPrivilegeWillExpire token: String) {
        Task { @MainActor in
            await renewToken()
        }
    }

    nonisolated func rtcEngine(_ engine: AgoraRtcEngineKit, didOfflineOfUid uid: UInt, reason: AgoraUserOfflineReason) {
        Task { @MainActor in
            if remoteUid == uid {
                remoteUid = nil
            }
            remoteParticipantJoined = false
            status = "The other person left the call"
        }
    }
}
#endif

enum AgoraCallMediaError: LocalizedError {
    case signedOut
    case invalidSession
    case sdkUnavailable
    case missingAppId
    case tokenMissing
    case joinFailed(Int32)

    var errorDescription: String? {
        switch self {
        case .signedOut: return "Sign in again before joining a call."
        case .invalidSession: return "This call is missing its secure channel."
        case .sdkUnavailable: return "The iOS target needs the AgoraRtcKit package before calls can connect."
        case .missingAppId: return "AGORA_APP_ID is missing from the iOS target configuration."
        case .tokenMissing: return "The secure call token was unavailable. Please try again."
        case .joinFailed(let code): return "Could not join the call (code \(code))."
        }
    }
}
