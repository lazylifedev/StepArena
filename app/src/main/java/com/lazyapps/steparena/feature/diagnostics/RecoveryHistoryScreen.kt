package com.lazyapps.steparena.feature.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.app.StepArenaApplication
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import com.lazyapps.steparena.recovery.TrackingGapStatus

@Composable
fun RecoveryHistoryScreen() {
    val app = LocalContext.current.applicationContext as StepArenaApplication
    val records by app.gapRecoveryRepository.observeHistory().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("補完履歴", style = MaterialTheme.typography.headlineMedium) }
        if (records.isEmpty()) item { Text("補完履歴はありません") }
        items(records, key = { it.id }) { record ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${format(record.startedAtEpochMillis, record.zoneId)} ～ " +
                            format(record.endedAtEpochMillis, record.zoneId),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("理由: ${record.reason.name}")
                    Text("状態: ${record.status.name}")
                    Text("補完: ${record.recoveredSteps}歩 / 未解決: ${record.unresolvedSteps}歩")
                    Text("品質: ${record.quality.name}")
                    Text(
                        if (record.externalOriginsJson.isNullOrBlank()) {
                            "データ元: なし"
                        } else {
                            "データ元: 外部アプリ（詳細 ${record.externalOriginsJson}）"
                        },
                    )
                    if (record.status in setOf(
                            TrackingGapStatus.DETECTED,
                            TrackingGapStatus.RECOVERY_PENDING,
                            TrackingGapStatus.PARTIALLY_RECOVERED,
                            TrackingGapStatus.UNRESOLVED,
                            TrackingGapStatus.USER_REVIEW_REQUIRED,
                        ) && !record.explicitUserStop
                    ) {
                        Button(
                            onClick = {
                                scope.launch { app.gapRecoveryRepository.recover(record.id) }
                            },
                        ) { Text("Health Connectから補完を試す") }
                    }
                }
            }
        }
    }
}

private fun format(epochMillis: Long, zoneId: String): String =
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").format(
        Instant.ofEpochMilli(epochMillis).atZone(
            runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault()),
        ),
    )
