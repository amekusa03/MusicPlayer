package com.kusa.musicplayer.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver

/**
 * 【重要】このクラスは現在 AndroidManifest.xml でコメントアウトされています。
 * 
 * 【理由】CarAppLibrary（Car App Service）でカスタム画面を定義すると、
 * Android Auto は以下のように動作します:
 * 
 *   1. アプリが「カスタム画面を出している」と判断
 *   2. Media3 の標準再生UI（シャッフルボタン、大きなアートワーク）を表示しない
 *   3. この画面（NowPlayingScreen）だけが表示される
 * 
 * 結果: シャッフルボタンやアートワークが見えない状態に
 * 
 * 【ベストプラクティス】
 * 音楽アプリの場合は:
 *   - Media3（MediaLibraryService）が自動的に「標準の豪華な再生UI」を提供
 *   - CarAppService は「曲リストをブラウズ」にのみ使うべき
 *   - 再生画面そのものはカスタマイズしない
 * 
 * 【再度有効にしたい場合の注意】
 * AndroidManifest.xml の MusicCarAppService のコメント行を参照してください。
 * onCreateScreen() で NowPlayingScreen を返さず、
 * MediaBrowser の結果を表示するブラウズ画面を返すべきです。
 */

/**
 * Simple Car session.  Android Auto's media template handles playback UI
 * automatically via the MediaSession.  This session provides a minimal
 * "Now Playing" info pane as a supplementary screen.
 * 
 * ⚠️ CURRENTLY DISABLED in AndroidManifest.xml (see class comment above)
 */
class MusicCarSession : Session() {

    override fun onCreateScreen(intent: android.content.Intent): Screen =
        NowPlayingScreen(carContext)
}

/**
 * ⚠️ 警告: このスクリーンが表示されるたびに、Media3 の標準UIが隠れます
 * 
 * 現象: 車に表示されるのは以下のテキストだけで、シャッフルボタンもアートワークも見えない
 *   "Music Player
 *    音楽を再生中 — Androidオートに対応しています"
 * 
 * この画面を返すことが、ユーザーが「機能が表示されない」と感じる直接的な原因です。
 * 
 * 【解決方法】
 * 1. AndroidManifest.xml で MusicCarAppService をコメントアウト（現在の状態）
 *    → Media3 の標準UIが表示されるようになる
 * 2. または、このスクリーンをブラウズ画面に変更し、再生はMedia3に任せる
 */
class NowPlayingScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        // ⚠️ このシンプルな PaneTemplate を返すと:
        //   1. Android Auto 側は「アプリが独自の画面を出している」と認識
        //   2. Media3 の標準再生UI を表示しない
        //   3. ユーザーは単なるテキスト情報しか見えない状態になる
        //
        // 結果: シャッフルボタン、アートワークなど、豊富な再生コントロールが全て隠れる
        
        val row = Row.Builder()
            .setTitle("Music Player")
            .addText("音楽を再生中 — Androidオートに対応しています")
            .build()

        val pane = Pane.Builder()
            .addRow(row)
            .build()

        return PaneTemplate.Builder(pane)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
