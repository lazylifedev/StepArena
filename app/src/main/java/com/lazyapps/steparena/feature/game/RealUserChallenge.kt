package com.lazyapps.steparena.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.lazyapps.steparena.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.lazyapps.steparena.BuildConfig
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.designsystem.theme.StepArenaColors
import com.lazyapps.steparena.game.OfficialSteps
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class RealUserPartnerProgress(
    val officialSteps: Long = 0,
    val competitionSteps: Long = 0,
    val rewardSteps: Long = 0,
    val result: RealUserResult = RealUserResult.PENDING,
    val syncState: RealUserSyncState = RealUserSyncState.PENDING,
    val progressUpdatedAt: com.google.firebase.Timestamp? = null,
)

enum class RealUserResult { WIN, LOSS, DRAW, PENDING, UNKNOWN }
enum class RealUserSyncState { FINALIZED, SYNCED, PENDING, UNKNOWN }
enum class RealUserChallengeStatus { ACTIVE, FINALIZED, UNKNOWN }
private val RealUserResult.resource get() = when (this) {
    RealUserResult.WIN -> R.string.real_user_challenge_result_win
    RealUserResult.LOSS -> R.string.real_user_challenge_result_loss
    RealUserResult.DRAW -> R.string.real_user_challenge_result_draw
    RealUserResult.PENDING -> R.string.real_user_challenge_result_pending
    RealUserResult.UNKNOWN -> R.string.real_user_challenge_result_unknown
}
private val RealUserSyncState.resource get() = when (this) {
    RealUserSyncState.FINALIZED -> R.string.real_user_challenge_sync_finalized
    RealUserSyncState.SYNCED -> R.string.real_user_challenge_sync_synced
    RealUserSyncState.PENDING -> R.string.real_user_challenge_sync_pending
    RealUserSyncState.UNKNOWN -> R.string.real_user_challenge_sync_unknown
}

internal fun String?.toRealUserResult() = when (this) {
    "win" -> RealUserResult.WIN
    "loss" -> RealUserResult.LOSS
    "draw" -> RealUserResult.DRAW
    "pending" -> RealUserResult.PENDING
    else -> RealUserResult.UNKNOWN
}

internal fun String?.toRealUserSyncState() = when (this) {
    "finalized" -> RealUserSyncState.FINALIZED
    "synced" -> RealUserSyncState.SYNCED
    "pending" -> RealUserSyncState.PENDING
    else -> RealUserSyncState.UNKNOWN
}

internal fun String?.toRealUserChallengeStatus() = when (this) {
    "active" -> RealUserChallengeStatus.ACTIVE
    "finalized", "completed" -> RealUserChallengeStatus.FINALIZED
    else -> RealUserChallengeStatus.UNKNOWN
}

internal fun realUserPartnerProgressFromFirestore(data: Map<String, Any?>) = RealUserPartnerProgress(
    officialSteps = (data["officialSteps"] as? Number)?.toLong() ?: 0,
    competitionSteps = (data["competitionSteps"] as? Number)?.toLong() ?: 0,
    rewardSteps = (data["rewardSteps"] as? Number)?.toLong() ?: 0,
    result = (data["result"] as? String).toRealUserResult(),
    syncState = (data["syncState"] as? String).toRealUserSyncState(),
)

internal fun realUserDisplayedSteps(progress: RealUserPartnerProgress, status: RealUserChallengeStatus) =
    if (status == RealUserChallengeStatus.ACTIVE) {
        OfficialSteps.competition(progress.officialSteps)
    } else {
        OfficialSteps.competition(progress.competitionSteps)
    }

internal fun realUserShowsFinalizedDetails(status: RealUserChallengeStatus) =
    status == RealUserChallengeStatus.FINALIZED

data class RealUserChallengeState(
    val challengeId: String? = null,
    val status: RealUserChallengeStatus = RealUserChallengeStatus.UNKNOWN,
    val opponentName: String = "Opponent",
    val opponentProgress: RealUserPartnerProgress? = null,
    val error: String? = null,
    val selfDisplayName: String = "You",
    val selfProgress: RealUserPartnerProgress? = null,
    val challengeStatus: RealUserChallengeStatus = status,
    val recoveryError: RealUserChallengeRecoveryError? = null,
)

object RealUserChallengeTestTags {
    const val SELF_PROGRESS = "real_user_self_progress"
    const val OPPONENT_PROGRESS = "real_user_opponent_progress"
}

sealed interface RealUserUiState { data object Idle: RealUserUiState; data object SubmittingProgress: RealUserUiState; data object SearchingPartner: RealUserUiState; data object WaitingForPartner: RealUserUiState; data object CreatingChallenge: RealUserUiState; data object NoPartner: RealUserUiState; data class Active(val challengeId:String):RealUserUiState; data object AuthenticationRequired:RealUserUiState; data object RetryableError:RealUserUiState; data object NonRetryableError:RealUserUiState }

sealed interface FindPartnerResult { data class Existing(val challengeId: String): FindPartnerResult; data object NoPartner: FindPartnerResult; data object Waiting: FindPartnerResult; data class Reserved(val reservationId: String, val opponentDisplayName: String): FindPartnerResult; data object InvalidResponse: FindPartnerResult }

class RealUserChallengeRemoteDataSource(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("us-central1"),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun findPartner(): FindPartnerResult { val d=functions.getHttpsCallable("findChallengePartner").call(mapOf<String,Any>()).await().data as? Map<*, *> ?: return FindPartnerResult.InvalidResponse; return when(d["status"]){"existing" -> (d["challengeId"] as? String)?.let { FindPartnerResult.Existing(it) } ?: FindPartnerResult.InvalidResponse; "no_partner" -> FindPartnerResult.NoPartner; "waiting" -> FindPartnerResult.Waiting; "reserved" -> { val r=d["reservationId"] as? String; if(r==null) FindPartnerResult.InvalidResponse else FindPartnerResult.Reserved(r,d["opponentDisplayName"] as? String ?: "Opponent") }; else -> FindPartnerResult.InvalidResponse} }

    suspend fun createChallenge(reservationId: String, requestId: String): String =
        (functions.getHttpsCallable("createChallengeCallable").call(mapOf("reservationId" to reservationId, "requestId" to requestId)).await().data as? Map<*, *>)
            ?.get("challengeId") as? String ?: error("missing_challenge_id")

    fun observeChallenge(challengeId: String, onState: (RealUserChallengeState) -> Unit): ListenerRegistration {
        var selfRegistration: ListenerRegistration?=null; var opponentRegistration: ListenerRegistration?=null
        var latestSelf: com.google.firebase.firestore.DocumentSnapshot?=null; var latestOpponent: com.google.firebase.firestore.DocumentSnapshot?=null
        var latestStatus = RealUserChallengeStatus.UNKNOWN; var latestIds: List<String> = emptyList()
        val challengeRegistration=firestore.collection("challenges").document(challengeId).addSnapshotListener { snapshot, error ->
            if (error != null) { onState(RealUserChallengeState(recoveryError = RealUserChallengeRecoveryError.TRANSIENT)); return@addSnapshotListener }
            val data = snapshot?.data
            if (data == null) { onState(RealUserChallengeState(challengeId = challengeId, recoveryError = RealUserChallengeRecoveryError.PERMANENT)); return@addSnapshotListener }
            latestStatus = (data["status"] as? String).toRealUserChallengeStatus(); val rawIds = (data["participantIds"] as? List<*>) ?: emptyList<Any?>()
            val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: run { onState(RealUserChallengeState(recoveryError=RealUserChallengeRecoveryError.PERMANENT)); return@addSnapshotListener }
            val validatedIds = validateRealUserParticipantIds(rawIds, me)
            val ids = rawIds.filterIsInstance<String>()
            val opponentUid = validatedIds?.opponentUid
                ?: run { onState(RealUserChallengeState(recoveryError=RealUserChallengeRecoveryError.PERMANENT)); return@addSnapshotListener }
            val participantsUnchanged=ids==latestIds && selfRegistration!=null && opponentRegistration!=null
            if(!participantsUnchanged){latestIds=ids; selfRegistration?.remove(); opponentRegistration?.remove(); latestSelf=null; latestOpponent=null}
            val collection=firestore.collection("challenges").document(challengeId).collection("participants")
            fun progress(snapshot: com.google.firebase.firestore.DocumentSnapshot) = realUserPartnerProgressFromFirestore(snapshot.data.orEmpty()).copy(progressUpdatedAt = snapshot.getTimestamp("progressUpdatedAt"))
            fun emit(){
                val s=latestSelf; val o=latestOpponent
                when (classifyParticipantSnapshots(s?.exists(), o?.exists())) {
                    ParticipantSnapshotState.WAITING -> return
                    ParticipantSnapshotState.PERMANENT_FAILURE -> {
                        onState(RealUserChallengeState(recoveryError=RealUserChallengeRecoveryError.PERMANENT)); return
                    }
                    ParticipantSnapshotState.READY -> Unit
                }
                requireNotNull(s); requireNotNull(o)
                onState(RealUserChallengeState(challengeId,latestStatus,o.getString("publicDisplayName") ?: "Opponent",progress(o),selfDisplayName=s.getString("publicDisplayName") ?: "You",selfProgress=progress(s),challengeStatus=latestStatus))
            }
            if(participantsUnchanged){emit();return@addSnapshotListener}
            selfRegistration=collection.document(me).addSnapshotListener { participant, participantError -> if(participantError!=null) onState(RealUserChallengeState(recoveryError=RealUserChallengeRecoveryError.TRANSIENT)) else {latestSelf=participant;emit()} }
            opponentRegistration=collection.document(opponentUid).addSnapshotListener { participant, participantError -> if(participantError!=null) onState(RealUserChallengeState(recoveryError=RealUserChallengeRecoveryError.TRANSIENT)) else {latestOpponent=participant;emit()} }
        }
        return object: ListenerRegistration { override fun remove(){challengeRegistration.remove();selfRegistration?.remove();opponentRegistration?.remove()} }
    }
}

class RealUserChallengeRepository(
    private val remote: RealUserChallengeRemoteDataSource = RealUserChallengeRemoteDataSource(),
) {
    private var activeReservationId: String? = null
    private var activeRequestId: String? = null
    fun reset() { activeReservationId = null; activeRequestId = null }
    suspend fun findAndCreate(submit: suspend () -> Unit): String {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: error("auth_required")
        submit()
        return when(val partner=remote.findPartner()){is FindPartnerResult.Existing -> partner.challengeId.also { activeReservationId=null; activeRequestId=null }; FindPartnerResult.NoPartner -> {activeReservationId=null;activeRequestId=null;error("no_partner")}; FindPartnerResult.Waiting -> error("waiting"); is FindPartnerResult.Reserved -> {if (activeReservationId != partner.reservationId) { activeReservationId=partner.reservationId; activeRequestId=UUID.randomUUID().toString() }; val requestId=requireNotNull(activeRequestId); val challengeId=try {remote.createChallenge(partner.reservationId,requestId)} catch (e: Exception) { throw e }; activeReservationId=null; activeRequestId=null; challengeId}; FindPartnerResult.InvalidResponse -> error("invalid_response")}
    }

    fun observe(challengeId: String, onState: (RealUserChallengeState) -> Unit) = remote.observeChallenge(challengeId, onState)
}

object RealUserChallengeSession {
    var resetActiveChallenge: (() -> Unit)? = null
    fun reset() { resetActiveChallenge?.invoke() }
}

@Composable
fun RealUserChallengeScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as StepArenaApplication
    val repository = remember { RealUserChallengeRepository() }
    val scope = rememberCoroutineScope()
    val localStore = remember { RealUserChallengeLocalStore(context) }
    var state by remember { mutableStateOf(RealUserChallengeState()) }
    var uiState by remember { mutableStateOf<RealUserUiState>(RealUserUiState.Idle) }
    var listener by remember { mutableStateOf<ListenerRegistration?>(null) }
    LaunchedEffect(Unit) {
        listener?.remove()
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        val saved = localStore.read() ?: return@LaunchedEffect
        if (!localStore.belongsTo(saved, uid)) {
            localStore.clear()
            return@LaunchedEffect
        }
        listener = repository.observe(saved.challengeId) { observed ->
            if (BuildConfig.FLAVOR == "qa") {
                scope.launch {
                    app.qaTelemetry.recordEvent(
                        "CHALLENGE_STATE",
                        mapOf(
                            "challenge_state" to observed.challengeStatus.name,
                            "listener_state" to if (observed.recoveryError == null) "connected" else "error",
                            "self_competition_steps" to observed.selfProgress?.competitionSteps,
                            "opponent_competition_steps" to observed.opponentProgress?.competitionSteps,
                            "self_reward_steps" to observed.selfProgress?.rewardSteps,
                            "opponent_reward_steps" to observed.opponentProgress?.rewardSteps,
                        ),
                    )
                }
            }
            if (observed.recoveryError == RealUserChallengeRecoveryError.PERMANENT) {
                scope.launch { localStore.clear() }
                listener?.remove(); listener = null
                state = RealUserChallengeState()
                uiState = RealUserUiState.Idle
            } else if (observed.recoveryError == RealUserChallengeRecoveryError.TRANSIENT) {
                state = observed.copy(recoveryError = null)
                uiState = RealUserUiState.RetryableError
            } else {
                state = observed
                uiState = RealUserUiState.Active(saved.challengeId)
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        RealUserChallengeSession.resetActiveChallenge = {
            listener?.remove(); listener = null
            scope.launch { localStore.clear() }
            repository.reset()
            state = RealUserChallengeState()
            uiState = RealUserUiState.Idle
        }
        onDispose {
            listener?.remove()
            if (RealUserChallengeSession.resetActiveChallenge != null) RealUserChallengeSession.resetActiveChallenge = null
        }
    }
    if (BuildConfig.FLAVOR != "qa") return
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.real_user_challenge_title), style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("QA Functions: us-central1")
                val message = when (uiState) {
                    RealUserUiState.NoPartner -> stringResource(R.string.real_user_challenge_no_partner)
                    RealUserUiState.WaitingForPartner -> stringResource(R.string.real_user_challenge_waiting)
                    RealUserUiState.AuthenticationRequired -> stringResource(R.string.real_user_challenge_auth)
                    RealUserUiState.RetryableError -> stringResource(R.string.real_user_challenge_retry)
                    else -> null
                }
                message?.let { Text(it) }
                if (uiState is RealUserUiState.Active) {
                    val statusLabel = when (state.challengeStatus) {
                        RealUserChallengeStatus.ACTIVE -> stringResource(R.string.real_user_challenge_status_active)
                        RealUserChallengeStatus.FINALIZED -> stringResource(R.string.real_user_challenge_status_finalized)
                        RealUserChallengeStatus.UNKNOWN -> stringResource(R.string.real_user_challenge_status_unknown)
                    }
                    Text("${stringResource(R.string.real_user_challenge_status)}: $statusLabel")
                    @Composable
                    fun progress(name: String, value: RealUserPartnerProgress) {
                        Text(name)
                        val competitionSteps = realUserDisplayedSteps(value, state.challengeStatus)
                        Text("${stringResource(R.string.real_user_challenge_competition_steps)}: ${formatNumber(competitionSteps)}")
                        CompetitionProgressBar(
                            steps = competitionSteps,
                            baseColor = if (name == state.selfDisplayName) StepArenaColors.Cyan else StepArenaColors.Violet,
                            motionLevel = MotionLevel.FULL,
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .testTag(
                                    if (name == state.selfDisplayName) {
                                        RealUserChallengeTestTags.SELF_PROGRESS
                                    } else {
                                        RealUserChallengeTestTags.OPPONENT_PROGRESS
                                    },
                                ),
                        )
                        Text("${stringResource(R.string.real_user_challenge_official_steps)}: ${value.officialSteps}")
                        if (realUserShowsFinalizedDetails(state.challengeStatus)) {
                            Text("${stringResource(R.string.real_user_challenge_reward_steps)}: ${value.rewardSteps}")
                            Text("${stringResource(R.string.real_user_challenge_result)}: ${stringResource(value.result.resource)}")
                        }
                        Text("${stringResource(R.string.real_user_challenge_sync_state)}: ${stringResource(value.syncState.resource)}")
                        Text("${stringResource(R.string.real_user_challenge_updated)}: ${value.progressUpdatedAt?.toDate()?.toInstant() ?: stringResource(R.string.real_user_challenge_unknown)}")
                    }
                    state.selfProgress?.let { progress(state.selfDisplayName, it) }
                    state.opponentProgress?.let { progress(state.opponentName, it) }
                    if (realUserShouldShowNextChallenge(state.challengeStatus)) {
                        Button(onClick = { RealUserChallengeSession.reset() }) { Text(stringResource(R.string.real_user_challenge_next)) }
                    }
                }
                if (uiState !is RealUserUiState.Active) Button(enabled = uiState !is RealUserUiState.SubmittingProgress && uiState !is RealUserUiState.SearchingPartner && uiState !is RealUserUiState.CreatingChallenge, onClick = {
                    uiState = RealUserUiState.SubmittingProgress
                    scope.launch {
                        uiState = RealUserUiState.SearchingPartner
                        uiState = RealUserUiState.CreatingChallenge
                        state = runCatching { repository.findAndCreate { com.lazyapps.steparena.official.OfficialProgressRepository(app.activityRepository).submitToday() } }.fold(
                            onSuccess = { id ->
                                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                                    ?: error("auth_required")
                                localStore.save(id, user.uid)
                                listener?.remove()
                                listener = repository.observe(id) { observed ->
                                    if (observed.recoveryError == RealUserChallengeRecoveryError.PERMANENT) {
                                        scope.launch { localStore.clear() }
                                        listener?.remove(); listener = null
                                        state = RealUserChallengeState(); uiState = RealUserUiState.Idle
                                    } else if (observed.recoveryError == RealUserChallengeRecoveryError.TRANSIENT) {
                                        state = observed.copy(recoveryError=null); uiState = RealUserUiState.RetryableError
                                    } else { state = observed; uiState = RealUserUiState.Active(id) }
                                }
                                uiState = RealUserUiState.Active(id); RealUserChallengeState(id, RealUserChallengeStatus.ACTIVE)
                            },
                            onFailure = { when (it.message) { "no_partner" -> uiState = RealUserUiState.NoPartner; "waiting" -> uiState = RealUserUiState.WaitingForPartner; "auth_required" -> uiState = RealUserUiState.AuthenticationRequired; else -> uiState = RealUserUiState.RetryableError }; state.copy(error=null) },
                        )
                    }
                }) { Text(stringResource(when (uiState) { RealUserUiState.SubmittingProgress -> R.string.real_user_challenge_syncing; RealUserUiState.SearchingPartner -> R.string.real_user_challenge_searching; RealUserUiState.CreatingChallenge -> R.string.real_user_challenge_creating; RealUserUiState.WaitingForPartner -> R.string.real_user_challenge_waiting; else -> R.string.real_user_challenge_start })) }
            }
        }
    }
}
