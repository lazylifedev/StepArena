package com.lazyapps.steparena.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.BuildConfig
import com.lazyapps.steparena.release.DataManagementRepository
import com.lazyapps.steparena.release.DataUsage
import kotlinx.coroutines.launch

@Composable
fun DataManagementScreen(onDeleted: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { DataManagementRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<DataUsage?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var gameConfirm by remember { mutableStateOf(0) }
    var deleteConfirm by remember { mutableStateOf(0) }
    var resetConfirm by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) scope.launch {
            message = "書き出し中…"
            message = runCatching { repository.export(uri); "書き出しました" }
                .getOrElse { "書き出せませんでした（EXPORT-WRITE-01）" }
        }
    }
    LaunchedEffect(Unit) {
        usage = runCatching { repository.usage() }.getOrNull()
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
            .testTag("data_management_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("データ管理", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() })
        Text("データは端末内に保存され、ユーザー操作なしに外部へ送信されません。")
        usage?.let { UsageSummary(it) } ?: Text("読み込み中…")
        Button(
            onClick = { export.launch("StepArena-${java.time.LocalDate.now()}.zip") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("データを書き出す") }
        OutlinedButton(onClick = { gameConfirm = 1 }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("ゲーム進行だけ初期化")
        }
        OutlinedButton(onClick = { deleteConfirm = 1 }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("全データを削除")
        }
        OutlinedButton(onClick = { resetConfirm = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("設定だけ初期化")
        }
        message?.let { Text(it) }
    }
    if (gameConfirm > 0) AlertDialog(
        onDismissRequest = { gameConfirm = 0 },
        title = { Text(if (gameConfirm == 1) "ゲーム進行を初期化しますか？" else "本当に初期化しますか？") },
        text = { Text("ランク、対戦履歴、リーグ、シーズン、実績を削除します。歩数記録とプロフィールは残ります。") },
        confirmButton = {
            Button(onClick = {
                if (gameConfirm == 1) gameConfirm = 2 else scope.launch {
                    repository.resetGame(); gameConfirm = 0; usage = repository.usage()
                }
            }) { Text(if (gameConfirm == 1) "次へ" else "初期化する") }
        },
        dismissButton = { TextButton(onClick = { gameConfirm = 0 }) { Text("キャンセル") } },
    )
    if (deleteConfirm > 0) AlertDialog(
        onDismissRequest = { deleteConfirm = 0 },
        title = { Text("StepArena内の全データを削除") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Room、設定、通知、補完情報を削除します。Health Connectや他アプリの元データは削除しません。この操作は取り消せません。")
                if (deleteConfirm == 2) OutlinedTextField(
                    value = phrase, onValueChange = { phrase = it }, label = { Text("「削除」と入力") },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = deleteConfirm == 1 || phrase == "削除",
                onClick = {
                    if (deleteConfirm == 1) deleteConfirm = 2 else scope.launch {
                        repository.deleteAll(); deleteConfirm = 0; onDeleted()
                    }
                },
            ) { Text(if (deleteConfirm == 1) "次へ" else "完全に削除") }
        },
        dismissButton = { TextButton(onClick = { deleteConfirm = 0 }) { Text("キャンセル") } },
    )
    if (resetConfirm) AlertDialog(
        onDismissRequest = { resetConfirm = false },
        title = { Text("設定を初期化しますか？") },
        text = { Text("表示、通知、Health Connect補完、プロフィール設定を既定値へ戻します。記録とゲーム進行は残ります。") },
        confirmButton = {
            Button(onClick = {
                scope.launch { repository.resetSettings(keepOnboarding = true); resetConfirm = false }
            }) { Text("初期化") }
        },
        dismissButton = { TextButton(onClick = { resetConfirm = false }) { Text("キャンセル") } },
    )
}

@Composable private fun UsageSummary(u: DataUsage) {
    Text(
        "時間別 ${u.hourly}件 / 日次 ${u.daily}件 / セッション ${u.sessions}件\n" +
            "欠測 ${u.gaps}件 / Health Connect処理済み ${u.processedExternal}件\n" +
            "試合 ${u.matches}件 / リーグ ${u.leagues}件 / シーズン ${u.seasons}件 / 実績 ${u.achievements}件\n" +
            "通知 ${u.notificationEvents}件 / DB ${u.databaseBytes?.let(::formatBytes) ?: "取得できません"} / " +
            "設定 ${u.dataStoreBytes?.let(::formatBytes) ?: "取得できません"}\n" +
            "記録範囲 ${u.oldestDate ?: "なし"} ～ ${u.newestDate ?: "なし"}",
    )
}

private fun formatBytes(value: Long) = if (value < 1024) "${value} B" else "${value / 1024} KiB"

enum class InfoDocument { PRIVACY, TERMS, LICENSES, ABOUT }

@Composable
fun InfoDocumentScreen(document: InfoDocument) {
    val (title, body) = when (document) {
        InfoDocument.PRIVACY -> "プライバシーポリシー" to privacyText
        InfoDocument.TERMS -> "利用上の注意・免責" to termsText
        InfoDocument.LICENSES -> "オープンソースライセンス" to licenseText
        InfoDocument.ABOUT -> "アプリ情報" to "StepArena\nversion ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "package com.lazyapps.steparena\nDB schema 5\nZoneId ${java.time.ZoneId.systemDefault()}\nPrivacy 1 / Terms 1"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(body, modifier = Modifier.padding(top = 16.dp))
    }
}

private val privacyText = """
歩数、距離、歩行時間、推定カロリー、速度、身長・体重・歩幅、ゲーム進行、診断状態を端末内に保存します。
Health Connectは任意・既定OFFで、READ_STEPSだけを欠測補完に使用します。外部サーバー、広告、信用・雇用・保険判断には利用せず、第三者へ販売しません。
ユーザーが書き出しを選んだ場合を除き共有しません。アカウント、オンライン同期、広告・分析SDKはありません。
アプリ内の「全データを削除」またはアンインストールでStepArenaのデータを削除できます。Health Connectの元データと権限はHealth Connect側で管理します。
本アプリは子どものみを対象に設計していません。正式なサポート窓口は公開前に設定されます。
""".trimIndent()

private val termsText = """
StepArenaは医療機器ではありません。歩数、距離、カロリー、速度には誤差があり、端末やOSによって計測できない場合があります。
歩きながら端末を操作せず、交通・周辺環境と体調を優先してください。ゲーム結果のための無理な運動は避けてください。
必要な記録はエクスポートしてください。対戦相手はローカル生成NPCで、実在ユーザーではありません。
""".trimIndent()

private val licenseText = """
AndroidX / Jetpack Compose / Material 3 / Room / DataStore / WorkManager / Health Connect Client / Kotlin

各ライブラリはApache License 2.0等、それぞれの配布条件に従います。正確な依存バージョンはGradle Version Catalogを参照してください。
""".trimIndent()
