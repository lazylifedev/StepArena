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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.lazyapps.steparena.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.lazyapps.steparena.BuildConfig
import com.lazyapps.steparena.app.StepArenaApplication
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.UUID

data class RealUserPartnerProgress(
    val officialSteps: Long = 0,
    val syncState: String = "pending",
    val progressUpdatedAt: com.google.firebase.Timestamp? = null,
)

data class RealUserChallengeState(
    val challengeId: String? = null,
    val status: String = "none",
    val opponentName: String = "Opponent",
    val opponentProgress: RealUserPartnerProgress? = null,
    val error: String? = null,
    val selfDisplayName: String = "You",
    val selfProgress: RealUserPartnerProgress? = null,
    val challengeStatus: String = status,
)

sealed interface RealUserUiState { data object Idle: RealUserUiState; data object SubmittingProgress: RealUserUiState; data object SearchingPartner: RealUserUiState; data object CreatingChallenge: RealUserUiState; data object NoPartner: RealUserUiState; data class Active(val challengeId:String):RealUserUiState; data object AuthenticationRequired:RealUserUiState; data object RetryableError:RealUserUiState; data object NonRetryableError:RealUserUiState }

sealed interface FindPartnerResult { data class Existing(val challengeId: String): FindPartnerResult; data object NoPartner: FindPartnerResult; data object Waiting: FindPartnerResult; data class Reserved(val reservationId: String, val opponentDisplayName: String): FindPartnerResult; data object InvalidResponse: FindPartnerResult }

class RealUserChallengeRemoteDataSource(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("us-central1"),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun findPartner(): FindPartnerResult { val d=functions.getHttpsCallable("findChallengePartner").call(mapOf<String,Any>()).await().data as? Map<*, *> ?: return FindPartnerResult.InvalidResponse; return when(d["status"]){"existing" -> (d["challengeId"] as? String)?.let { FindPartnerResult.Existing(it) } ?: FindPartnerResult.InvalidResponse; "no_partner" -> FindPartnerResult.NoPartner; "reserved" -> { val r=d["reservationId"] as? String; if(r==null) FindPartnerResult.InvalidResponse else FindPartnerResult.Reserved(r,d["opponentDisplayName"] as? String ?: "Opponent") }; else -> FindPartnerResult.InvalidResponse} }

    suspend fun createChallenge(reservationId: String, requestId: String): String =
        (functions.getHttpsCallable("createChallengeCallable").call(mapOf("reservationId" to reservationId, "requestId" to requestId)).await().data as? Map<*, *>)
            ?.get("challengeId") as? String ?: error("missing_challenge_id")

    fun observeChallenge(challengeId: String, onState: (RealUserChallengeState) -> Unit): ListenerRegistration {
        var selfRegistration: ListenerRegistration?=null; var opponentRegistration: ListenerRegistration?=null
        var latestSelf: com.google.firebase.firestore.DocumentSnapshot?=null; var latestOpponent: com.google.firebase.firestore.DocumentSnapshot?=null
        var latestStatus="unknown"; var latestIds: List<String> = emptyList()
        val challengeRegistration=firestore.collection("challenges").document(challengeId).addSnapshotListener { snapshot, error ->
            if (error != null) { onState(RealUserChallengeState(error = "challenge_read_failed")); return@addSnapshotListener }
            val data = snapshot?.data ?: return@addSnapshotListener
            latestStatus=data["status"] as? String ?: "unknown"; val ids = (data["participantIds"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: run { onState(RealUserChallengeState(error="authentication_required")); return@addSnapshotListener }
            val opponentUid = ids.firstOrNull { it != me } ?: return@addSnapshotListener
            val participantsUnchanged=ids==latestIds && selfRegistration!=null && opponentRegistration!=null
            if(!participantsUnchanged){latestIds=ids; selfRegistration?.remove(); opponentRegistration?.remove(); latestSelf=null; latestOpponent=null}
            val collection=firestore.collection("challenges").document(challengeId).collection("participants")
            fun emit(){ val s=latestSelf; val o=latestOpponent; onState(RealUserChallengeState(challengeId,latestStatus,o?.getString("publicDisplayName") ?: "Opponent",o?.let { RealUserPartnerProgress(it.getLong("officialSteps") ?: 0,it.getString("syncState") ?: "unknown",it.getTimestamp("progressUpdatedAt"))},selfDisplayName=s?.getString("publicDisplayName") ?: "You",selfProgress=s?.let { RealUserPartnerProgress(it.getLong("officialSteps") ?: 0,it.getString("syncState") ?: "unknown",it.getTimestamp("progressUpdatedAt"))},challengeStatus=latestStatus)) }
            if(participantsUnchanged){emit();return@addSnapshotListener}
            selfRegistration=collection.document(me).addSnapshotListener { participant, participantError -> if(participantError!=null) onState(RealUserChallengeState(error="participant_read_failed")) else {latestSelf=participant;emit()} }
            opponentRegistration=collection.document(opponentUid).addSnapshotListener { participant, participantError -> if(participantError!=null) onState(RealUserChallengeState(error="participant_read_failed")) else {latestOpponent=participant;emit()} }
        }
        return object: ListenerRegistration { override fun remove(){challengeRegistration.remove();selfRegistration?.remove();opponentRegistration?.remove()} }
    }
}

class RealUserChallengeRepository(
    private val remote: RealUserChallengeRemoteDataSource = RealUserChallengeRemoteDataSource(),
) {
    private var activeRequestId: String? = null
    suspend fun findAndCreate(submit: suspend () -> Unit): String {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: error("auth_required")
        submit()
        return when(val partner=remote.findPartner()){is FindPartnerResult.Existing -> partner.challengeId; FindPartnerResult.NoPartner -> {activeRequestId=null;error("no_partner")}; FindPartnerResult.Waiting -> error("waiting"); is FindPartnerResult.Reserved -> {val id=activeRequestId ?: UUID.randomUUID().toString().also {activeRequestId=it}; try {remote.createChallenge(partner.reservationId,id)} finally {activeRequestId=null}}; FindPartnerResult.InvalidResponse -> error("invalid_response")}
    }

    fun observe(challengeId: String, onState: (RealUserChallengeState) -> Unit) = remote.observeChallenge(challengeId, onState)
}

@Composable
fun RealUserChallengeScreen() {
    val app = LocalContext.current.applicationContext as StepArenaApplication
    val repository = remember { RealUserChallengeRepository() }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(RealUserChallengeState()) }
    var loading by remember { mutableStateOf(false) }
    var listener by remember { mutableStateOf<ListenerRegistration?>(null) }
    LaunchedEffect(Unit) { listener?.remove() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { listener?.remove() }
    }
    if (BuildConfig.FLAVOR != "qa") return
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.real_user_challenge_title), style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("QA Functions: us-central1")
                Text("${stringResource(R.string.real_user_challenge_sync_state)}: ${state.status}")
                state.opponentProgress?.let {
                    Text("${stringResource(R.string.real_user_challenge_opponent)}: ${state.opponentName}")
                    Text("${stringResource(R.string.real_user_challenge_official_steps)}: ${it.officialSteps}")
                    Text("${stringResource(R.string.real_user_challenge_sync_state)}: ${it.syncState}")
                    Text("${stringResource(R.string.real_user_challenge_updated)}: ${it.progressUpdatedAt?.toDate()?.toInstant() ?: "unknown"}")
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = !loading, onClick = {
                    loading = true
                    scope.launch {
                        state = runCatching { repository.findAndCreate { com.lazyapps.steparena.official.OfficialProgressRepository(app.activityRepository).submitToday() } }.fold(
                            onSuccess = { id -> listener?.remove(); listener = repository.observe(id) { state = it }; RealUserChallengeState(id, "active") },
                            onFailure = { RealUserChallengeState(error = it.message ?: "challenge_failed") },
                        )
                        loading = false
                    }
                }) { Text(stringResource(if (loading) R.string.real_user_challenge_starting else R.string.real_user_challenge_start)) }
            }
        }
    }
}
