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
)

class RealUserChallengeRemoteDataSource(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("us-central1"),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun findPartner(): Map<*, *>? = functions.getHttpsCallable("findChallengePartner").call().await().data as? Map<*, *>

    suspend fun createChallenge(reservationId: String, requestId: String): String =
        (functions.getHttpsCallable("createChallengeCallable").call(mapOf("reservationId" to reservationId, "requestId" to requestId)).await().data as? Map<*, *>)
            ?.get("challengeId") as? String ?: error("missing_challenge_id")

    fun observeChallenge(challengeId: String, onState: (RealUserChallengeState) -> Unit): ListenerRegistration =
        firestore.collection("challenges").document(challengeId).addSnapshotListener { snapshot, error ->
            if (error != null) { onState(RealUserChallengeState(error = "challenge_read_failed")); return@addSnapshotListener }
            val data = snapshot?.data ?: return@addSnapshotListener
            val ids = (data["participantIds"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            val opponentUid = ids.firstOrNull { it != me } ?: return@addSnapshotListener
                val name = "Opponent"
                firestore.collection("challenges").document(challengeId).collection("participants").document(opponentUid)
                    .addSnapshotListener { participant, participantError ->
                        if (participantError != null) return@addSnapshotListener
                    onState(RealUserChallengeState(challengeId, data["status"] as? String ?: "unknown", participant?.getString("publicDisplayName") ?: name,
                            participant?.let { RealUserPartnerProgress(it.getLong("officialSteps") ?: 0, it.getString("syncState") ?: "unknown", it.getTimestamp("progressUpdatedAt")) }))
                    }
        }
}

class RealUserChallengeRepository(
    private val remote: RealUserChallengeRemoteDataSource = RealUserChallengeRemoteDataSource(),
) {
    suspend fun findAndCreate(): String {
        val partner = remote.findPartner() ?: error("no_partner_found")
        val reservationId = partner["reservationId"] as? String ?: error("invalid_reservation")
        return remote.createChallenge(reservationId, UUID.randomUUID().toString())
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
        Text("Real-user challenge", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("QA Functions: us-central1")
                Text("Status: ${state.status}")
                state.opponentProgress?.let {
                    Text("Opponent: ${state.opponentName}")
                    Text("Opponent official steps: ${it.officialSteps}")
                    Text("Sync: ${it.syncState}")
                    Text("Updated: ${it.progressUpdatedAt?.toDate()?.toInstant() ?: "unknown"}")
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = !loading, onClick = {
                    loading = true
                    scope.launch {
                        state = runCatching { repository.findAndCreate() }.fold(
                            onSuccess = { id -> listener?.remove(); listener = repository.observe(id) { state = it }; RealUserChallengeState(id, "active") },
                            onFailure = { RealUserChallengeState(error = it.message ?: "challenge_failed") },
                        )
                        loading = false
                    }
                }) { Text(if (loading) "Starting..." else "Start real-user challenge") }
            }
        }
    }
}
