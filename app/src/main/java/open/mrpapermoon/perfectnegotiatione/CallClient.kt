package open.mrpapermoon.perfectnegotiatione

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.annotation.CallSuper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.webrtc.*
import java.io.File
import kotlin.coroutines.suspendCoroutine

open class CallClient(
    context: Context,
    iceServers: List<PeerConnection.IceServer>,
    private val config: Config,
    private val callScope: CoroutineScope,
    private val webrtcLogger: (String) -> Unit
) : PeerConnectionObserver(webrtcLogger), RtcEvent {

    private val outgoingMediaConstraints = MediaConstraints()
    private val audioTrack: AudioTrack
    private val peerConnectionFactory: PeerConnectionFactory
    private val rtcConfiguration: PeerConnection.RTCConfiguration

    private var peerConnection: PeerConnection? = null

    init {
        val loggable = Loggable { message, severity, tag ->
            webrtcLogger("$TAG - $tag - $severity: $message")
        }

        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setInjectableLogger(loggable, Logging.Severity.LS_VERBOSE)
            .createInitializationOptions()

        PeerConnectionFactory.initialize(initializationOptions)

        val eglBase = EglBase.create()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        outgoingMediaConstraints.apply {
            mandatory.add(
                MediaConstraints.KeyValuePair(
                    "OfferToReceiveAudio",
                    config.audioChannelEnabled.toString()
                )
            )
            mandatory.add(
                MediaConstraints.KeyValuePair(
                    "OfferToReceiveVideo",
                    config.videoChannelEnabled.toString()
                )
            )
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }

        val audioSource = peerConnectionFactory.createAudioSource(outgoingMediaConstraints)
        audioTrack = peerConnectionFactory.createAudioTrack("voice", audioSource)

        rtcConfiguration = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXCOMPAT
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.NEGOTIATE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY

            iceCheckMinInterval = ICE_CD_MS
            iceConnectionReceivingTimeout = ICE_TTL_MS
            iceBackupCandidatePairPingInterval = COLLECT_BACKUP_ICE_EVERY_MS
        }

        config.rawLogFile
            .also {
                if (it.exists()) it.delete()
                it.createNewFile()
            }

        callScope.launch {
            webrtcLogger("Subscribes to network states")
            MutableStateFlow(Any()).collectLatest {
                //check network state: if network.state == CONNECTED
                if (true) {
                    webrtcLogger("Init p2p on WebSocketState.CONNECTED")
                    connect()
                }
            }
        }
    }

    override fun onIce(ice: IceCandidate) {
        peerConnection?.addIceCandidate(ice)
    }

    override fun removeIce(ice: IceCandidate) {
        peerConnection?.removeIceCandidates(arrayOf(ice))
    }

    override fun onSdp(sdp: SessionDescription) {
        val connection = peerConnection ?: return
        when (val type = sdp.type) {
            SessionDescription.Type.ANSWER -> {
                if (connection.signalingState() == PeerConnection.SignalingState.STABLE || connection.localDescription == null) return
                callScope.launch {
                    suspendCoroutine { continuation ->
                        val completable = SdpObserverCall.Completable(continuation)
                        webrtcLogger("setRemoteDescription >$type")
                        connection.setRemoteDescription(completable, sdp)
                    }
                }

            }
            else -> {}
        }
    }

    final override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
        super.onConnectionChange(newState)
        when (newState) {
            PeerConnection.PeerConnectionState.FAILED -> {
                webrtcLogger("restartIce on PeerConnectionState FAILED")
                peerConnection?.restartIce()
            }
            PeerConnection.PeerConnectionState.DISCONNECTED -> {
                //check network state: if network.state == CONNECTED
                if (true) {
                    webrtcLogger("emit p2p reconnect")
                    connect()
                }
            }
            else -> {}
        }
    }

    final override fun onRenegotiationNeeded() {
        super.onRenegotiationNeeded()
        callScope.launch {
            peerConnection?.also { createOffer(it) }
        }
    }

    @CallSuper
    open fun clear() {
        webrtcLogger("clear call engine: connectionState>${peerConnection?.connectionState()}; signalingState>${peerConnection?.signalingState()};")
        val peerConnection = peerConnection
        this.peerConnection = null
        peerConnection?.dispose()
    }

    private suspend fun createOffer(peerConnection: PeerConnection) {
        webrtcLogger("Creating offer...")

        suspendCoroutine {
            val completable = SdpObserverCall.Completable(it)
            peerConnection.setLocalDescription(completable)
        }
        yield()
        //sendOffer(peerConnection.localDescription)
    }

    private fun connect() {
        peerConnection?.run {
            webrtcLogger(
                "reconnect:\n" +
                        "ice>${iceConnectionState()}\n" +
                        "gathering>${iceGatheringState()}\n" +
                        "connection>${connectionState()}\n" +
                        "signaling>${signalingState()}"
            )


            if (connectionState() != PeerConnection.PeerConnectionState.DISCONNECTED) {
                webrtcLogger("Connection is stable")
                return
            }

            close()
        }

        webrtcLogger("Creating new peer connection…")
        val connection = createPeerConnection()
        val fileDescriptor = ParcelFileDescriptor.open(
            config.rawLogFile,
            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        )
            .detachFd()
        connection.startRtcEventLog(fileDescriptor, config.rawLogFileSize)
        connection.addTrack(audioTrack, listOf(audioTrack.id()))
        peerConnection = connection

        webrtcLogger("Awaiting for connection to complete...")
    }

    private fun createPeerConnection(): PeerConnection {
        return peerConnectionFactory.createPeerConnection(rtcConfiguration, this)
            ?: throw IllegalStateException("Peer connection is null >$rtcConfiguration")
    }

    data class Config(
        val audioChannelEnabled: Boolean,
        val videoChannelEnabled: Boolean,
        val rawLogFile: File,
        val rawLogFileSize: Int = 1_048_576,
    )

    private companion object {
        const val TAG = "WEB_RTC"

        const val ICE_CD_MS = 10
        const val ICE_TTL_MS = 1000
        const val COLLECT_BACKUP_ICE_EVERY_MS = 2500
    }
}