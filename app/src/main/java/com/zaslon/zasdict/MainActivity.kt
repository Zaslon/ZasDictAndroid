package com.zaslon.zasdict

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.zaslon.zasdict.ui.AppSnackbarVisuals
import com.zaslon.zasdict.ui.ChangelogScreen
import com.zaslon.zasdict.ui.DetailScreen
import com.zaslon.zasdict.ui.DictionarySettingsScreen
import com.zaslon.zasdict.ui.EditorScreen
import com.zaslon.zasdict.ui.ExampleEditorScreen
import com.zaslon.zasdict.ui.ExamplesScreen
import com.zaslon.zasdict.ui.LegendScreen
import com.zaslon.zasdict.ui.SearchScreen
import com.zaslon.zasdict.ui.SettingsScreen
import com.zaslon.zasdict.ui.ToolsScreen
import com.zaslon.zasdict.ui.VolumeKeyScroller
import com.zaslon.zasdict.ui.theme.ZasDictTheme

object Routes {
    const val SEARCH = "search"
    const val DETAIL = "detail/{wordId}"
    const val EDITOR = "editor"
    const val TOOLS = "tools/{tab}"
    const val LEGEND = "legend"
    const val CHANGELOG = "changelog"
    const val SETTINGS = "settings"
    const val DICT_SETTINGS = "dictSettings"
    const val EXAMPLES = "examples"
    const val EXAMPLE_EDITOR = "exampleEditor"

    fun detail(wordId: Int) = "detail/$wordId"
    fun tools(tab: Int) = "tools/$tab"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 保存された画面の状態が無い＝OS に終了されたのではなく、利用者が自分で閉じた後の起動
        val launchedFresh = savedInstanceState == null
        setContent {
            val vm: MainViewModel = viewModel()
            LaunchedEffect(Unit) {
                if (launchedFresh) vm.discardStashedDrafts()
            }
            ZasDictTheme(einkMode = vm.einkMode) {
                ZasDictApp(vm)
            }
        }
    }

    /** OAuth2 リダイレクト URI を受け取る（launchMode="singleTop" で呼ばれる） */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data ?: return
        when {
            uri.scheme == "zasdict" && uri.host == "dropbox-auth" -> {
                val code = uri.getQueryParameter("code") ?: return
                MainViewModel.pendingDropboxAuthCode.value = code
            }
            uri.scheme == "zasdict" && uri.host == "box-auth" -> {
                val code = uri.getQueryParameter("code") ?: return
                MainViewModel.pendingBoxAuthCode.value = code
            }
        }
    }

    /** 音量キーを画面スクロールに割り当てる（処理されなかった場合は通常の音量操作） */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val handled = VolumeKeyScroller.handler
                ?.invoke(keyCode == KeyEvent.KEYCODE_VOLUME_UP) ?: false
            if (handled) return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun ZasDictApp(vm: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp.toFloat() >= configuration.screenHeightDp.toFloat() * 1.5f

    // 新しい通知が来ると、表示中の通知は取り下げられて最新の 1 件だけが出る
    LaunchedEffect(vm.message) {
        vm.message?.let {
            val result = snackbarHostState.showSnackbar(AppSnackbarVisuals(it))
            vm.consumeMessage()
            if (result == SnackbarResult.ActionPerformed) it.onAction?.invoke()
        }
    }

    // 処理中の表示は、処理が終わるまで出し続ける。終わると、この LaunchedEffect が取り消されて消える。
    LaunchedEffect(vm.isSyncing) {
        if (vm.isSyncing) {
            snackbarHostState.showSnackbar(
                AppSnackbarVisuals(AppMessage(vm.syncingStatus), duration = SnackbarDuration.Indefinite)
            )
        }
    }

    if (vm.promptRelinkChangelog) {
        AlertDialog(
            onDismissRequest = { vm.dismissRelinkPrompt() },
            title = { Text("更新履歴CSVの再連携") },
            text = {
                Text(
                    "保存先が変わったため、更新履歴CSVとの連携を解除しました。" +
                    "履歴は一旦アプリ内部に保存されます。\n" +
                    "新しい保存先に「辞書名_changelog.csv」を配置した場合は、" +
                    "更新履歴画面から選択して再連携してください（内部の履歴はCSVへ移行されます）。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissRelinkPrompt()
                    navController.navigate(Routes.CHANGELOG)
                }) { Text("更新履歴画面を開く") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissRelinkPrompt() }) { Text("後で") }
            }
        )
    }

    vm.errorNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { vm.dismissError() },
            title = { Text(notice.title) },
            // 問い合わせや検索に使えるよう、原因の文は選択してコピーできるようにする
            text = { SelectionContainer { Text(notice.body) } },
            confirmButton = {
                TextButton(onClick = { vm.dismissError() }) { Text("閉じる") }
            }
        )
    }

    val eink = vm.einkMode
    NavHost(
        navController = navController,
        startDestination = Routes.SEARCH,
        enterTransition = { if (eink) EnterTransition.None else fadeIn(animationSpec = tween(220)) },
        exitTransition = { if (eink) ExitTransition.None else fadeOut(animationSpec = tween(220)) },
        popEnterTransition = { if (eink) EnterTransition.None else fadeIn(animationSpec = tween(220)) },
        popExitTransition = { if (eink) ExitTransition.None else fadeOut(animationSpec = tween(220)) }
    ) {
        composable(Routes.SEARCH) {
            if (isTablet) {
                TabletSearchDetailLayout(vm, navController, snackbarHostState)
            } else {
                SearchScreen(vm, navController, snackbarHostState)
            }
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("wordId") { type = NavType.IntType })
        ) { backStackEntry ->
            val wordId = backStackEntry.arguments?.getInt("wordId") ?: -1
            DetailScreen(vm, navController, wordId, snackbarHostState)
        }
        composable(Routes.EDITOR) {
            EditorScreen(vm, navController, snackbarHostState)
        }
        composable(
            Routes.TOOLS,
            arguments = listOf(navArgument("tab") { type = NavType.IntType })
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getInt("tab") ?: 0
            ToolsScreen(vm, navController, tab, snackbarHostState)
        }
        composable(Routes.LEGEND) {
            LegendScreen(vm, navController, snackbarHostState)
        }
        composable(Routes.CHANGELOG) {
            ChangelogScreen(vm, navController, snackbarHostState)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(vm, navController, snackbarHostState)
        }
        composable(Routes.DICT_SETTINGS) {
            DictionarySettingsScreen(vm, navController, snackbarHostState)
        }
        composable(Routes.EXAMPLES) {
            ExamplesScreen(vm, navController, snackbarHostState)
        }
        composable(Routes.EXAMPLE_EDITOR) {
            ExampleEditorScreen(vm, navController, snackbarHostState)
        }
    }
}

@Composable
private fun TabletSearchDetailLayout(
    vm: MainViewModel,
    navController: NavController,
    snackbarHostState: SnackbarHostState
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            SearchScreen(
                vm = vm,
                navController = navController,
                snackbarHostState = snackbarHostState,
                onWordClick = { id -> vm.selectTabletWord(id) },
                selectedWordId = vm.tabletSelectedWordId
            )
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Box(modifier = Modifier.weight(1f)) {
            val selectedId = vm.tabletSelectedWordId
            if (selectedId != null) {
                DetailScreen(
                    vm = vm,
                    navController = navController,
                    wordId = selectedId,
                    snackbarHostState = snackbarHostState,
                    isPane = true,
                    onSelectWord = { id -> vm.selectTabletWord(id) }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "単語を選んでください",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
