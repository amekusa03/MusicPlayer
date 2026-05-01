# クサプレイヤー (MusicPlayer for Android)

Android 向けのシンプルなローカル音楽プレイヤーアプリです。

## 主な機能

- MP3ファイルの再生（フォルダ選択方式）
- アルバムアート表示
- シャッフル再生
- 検索（曲名・アーティスト名・アルバム名）
- バックグラウンド再生（フォアグラウンドサービス）
- Android Auto 対応
- Googleアシスタント音声操作対応（「〇〇をかけて」）
- スキャン結果のキャッシュ（24時間TTL）

## 技術スタック

| 項目 | 使用技術 |
|---|---|
| 言語 | Kotlin |
| 非同期処理 | Kotlin Coroutines |
| メディア再生 | AndroidX Media3 (ExoPlayer) |
| メディアセッション | Media3 MediaLibraryService |
| Android Auto | androidx.car.app |
| UI | RecyclerView, SearchView, SeekBar |
| 最小SDK | API 26 (Android 8.0) |

## ファイル構成

```
app/src/main/java/com/musicplayer/
├── MainActivity.kt              # メイン画面
├── AlbumArtLoader.kt            # アルバムアート読み込みユーティリティ
├── adapter/
│   └── SongListAdapter.kt       # 曲リスト RecyclerView アダプタ
├── car/
│   ├── MusicCarAppService.kt    # Android Auto エントリポイント
│   └── MusicCarSession.kt       # Android Auto セッション
├── model/
│   ├── Song.kt                  # 曲データクラス
│   ├── PlayMode.kt              # 再生モード（通常 / シャッフル）
│   └── MusicRepository.kt       # フォルダスキャン・キャッシュ管理
├── service/
│   └── MusicPlaybackService.kt  # バックグラウンド再生サービス
└── viewmodel/
    └── PlayerViewModel.kt       # UI ロジック（MediaController）
```

## 未達・既知の問題

- Googleアシスタントの「〇〇をかけて」が YouTube Music / Spotify 側で開かれる場合がある（OS側の優先アプリ設定に依存）

## セットアップ

1. Android Studio で本プロジェクトを開く
2. `app/build.gradle` の `compileSdk` / `targetSdk` がインストール済みSDKと一致していることを確認
3. 実機またはエミュレータで実行
4. 起動後、「フォルダ」ボタンからMP3が入っているフォルダを選択

## 開発について

このアプリは **Claude Code**（[Anthropic](https://www.anthropic.com/) の AI）を活用して開発しました。

- コード生成・設計・デバッグに Claude Code を使用
- AIが生成したコードは人間がレビューし、動作を確認しています

> Built with [Claude Code](https://claude.ai/code)

## ライセンス

[LICENSE.txt](LICENSE.txt) を参照してください。
