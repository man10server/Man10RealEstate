# BankAPI 仕様書

`red.man10.man10bank.BankAPI` の API 仕様書です。

`BankAPI` は、旧バージョンの呼び出しシグネチャを保持したまま、内部処理を Web API クライアント
（[`BankApiClient`](../src/main/java/red/man10/man10bank/api/BankApiClient.kt)）へ委譲する互換クラスです。
他プラグインから Man10Bank の入出金・残高照会を行うための公開エントリポイントとして使用します。

- 対象ソース: [`src/main/java/red/man10/man10bank/BankAPI.kt`](../src/main/java/red/man10/man10bank/BankAPI.kt)
- 互換目的のため `BankService` 側の修正やロケータは使用しません。

---

## 目次

- [概要](#概要)
- [初期化](#初期化)
- [同期 API（結果付き・推奨）](#同期-api結果付き推奨)
- [同期 API（互換・非推奨）](#同期-api互換非推奨)
- [非同期 API（結果付き・推奨）](#非同期-api結果付き推奨)
- [非同期 API（互換・非推奨）](#非同期-api互換非推奨)
- [残高照会](#残高照会)
- [データ型](#データ型)
- [エラーハンドリング](#エラーハンドリング)
- [スレッドモデルと注意点](#スレッドモデルと注意点)
- [使用例](#使用例)

---

## 概要

`BankAPI` は以下の機能を提供します。

| 機能 | 推奨メソッド（結果付き） | 互換メソッド（非推奨） |
| --- | --- | --- |
| 出金（同期） | `tryWithdraw` | `withdraw` |
| 入金（同期） | `tryDeposit` | `deposit` |
| 出金（非同期） | `asyncTryWithdraw` | `asyncWithdraw` |
| 入金（非同期） | `asyncTryDeposit` | `asyncDeposit` |
| 残高照会（同期） | `getBalance` | — |
| 残高照会（非同期） | `asyncGetBalance` | — |

- **推奨 API** は [`BankTransactionResult`](#banktransactionresult) を返し、成功可否に加えて失敗理由
  （HTTP ステータス・エラーメッセージ・`ProblemDetails`）を取得できます。
- **互換 API** は旧シグネチャ維持のため残されていますが、失敗理由が取得できないため `@Deprecated` 指定です。
  新規実装では推奨 API を使用してください。

内部では HTTP 経由で Bank Web API（`POST /api/Bank/deposit`、`POST /api/Bank/withdraw`、
`GET /api/Bank/{uuid}/balance`）を呼び出します。接続先・認証は `config.yml` の `api` セクションで設定します。

---

## 初期化

```kotlin
class BankAPI(private val plugin: JavaPlugin)
```

- **引数**: `plugin` — 呼び出し元プラグインのインスタンス。
  - 取引リクエストの `pluginName` として `plugin.name` が使用されます。
  - 非同期 API のスケジューラ（`runTaskAsynchronously` / `runTask`）に使用されます。

### 生成例

```kotlin
val bankApi = BankAPI(this) // this は呼び出し元の JavaPlugin
```

### 内部依存

- `Man10Bank` 本体プラグインがロードされている必要があります。未ロードの場合、各メソッドは
  「利用不可」を表す結果（[`BankTransactionResult.unavailable()`](#生成用ファクトリ) や `0.0`、`resultCode = -1`）を返します。
- API クライアントは初回アクセス時に遅延生成され、`config.yml` の設定を読み込みます。

---

## 同期 API（結果付き・推奨）

> ⚠️ これらは内部で `runBlocking` により HTTP 通信を行います。**メインスレッドでの呼び出しはサーバーを
> ブロックする恐れがある**ため、原則として[非同期 API](#非同期-api結果付き推奨) の使用を推奨します。

### `tryWithdraw`

```kotlin
fun tryWithdraw(uuid: UUID, amount: Double, note: String, displayNote: String): BankTransactionResult
```

指定プレイヤーの口座から出金します。

| 引数 | 型 | 説明 |
| --- | --- | --- |
| `uuid` | `UUID` | 対象プレイヤーの UUID |
| `amount` | `Double` | 出金額 |
| `note` | `String` | 内部用メモ（プレイヤーには表示されない） |
| `displayNote` | `String` | 表示用メモ（取引履歴等に表示される） |

- **戻り値**: [`BankTransactionResult`](#banktransactionresult)。`success` が `true` の場合 `balance` に取引後の新残高が入ります。
- **失敗時**: `errorMessage` / `httpStatus` / `problem` から理由を参照できます（残高不足など）。
- 本体未ロード時は `BankTransactionResult.unavailable()` を返します。

### `tryDeposit`

```kotlin
fun tryDeposit(uuid: UUID, amount: Double, note: String, displayNote: String): BankTransactionResult
```

指定プレイヤーの口座へ入金します。引数・戻り値は `tryWithdraw` と同様です。

---

## 同期 API（互換・非推奨）

いずれも内部で推奨メソッドへ委譲します。失敗理由を取得できないため非推奨です。

```kotlin
@Deprecated // tryWithdraw(uuid, amount, note, displayNote).success を推奨
fun withdraw(uuid: UUID, amount: Double, note: String, displayNote: String): Boolean

@Deprecated // tryWithdraw(uuid, amount, note, note).success を推奨
fun withdraw(uuid: UUID, amount: Double, note: String): Boolean

@Deprecated // tryDeposit(uuid, amount, note, displayNote) を推奨
fun deposit(uuid: UUID, amount: Double, note: String, displayNote: String)

@Deprecated // tryDeposit(uuid, amount, note, note) を推奨
fun deposit(uuid: UUID, amount: Double, note: String)
```

- `withdraw` は成功可否を `Boolean` で返します。
- `deposit` は戻り値を持たず（`Unit`）、結果を取得できません。
- `displayNote` を省略するオーバーロードでは `note` がそのまま `displayNote` として使用されます。

---

## 非同期 API（結果付き・推奨）

HTTP 通信を非同期スレッドで実行し、結果を**メインスレッド上の**コールバックへ通知します。
メインスレッドをブロックしないため、ゲーム内処理からの呼び出しに最適です。

### `asyncTryDeposit`

```kotlin
fun asyncTryDeposit(
    uuid: UUID,
    amount: Double,
    note: String,
    displayNote: String,
    callback: Bank.ResultCallback,
)
```

入金を非同期で行い、結果を `callback` へ通知します。

| 引数 | 型 | 説明 |
| --- | --- | --- |
| `uuid` | `UUID` | 対象プレイヤーの UUID |
| `amount` | `Double` | 入金額 |
| `note` | `String` | 内部用メモ |
| `displayNote` | `String` | 表示用メモ |
| `callback` | [`Bank.ResultCallback`](#bankresultcallback) | 結果通知用コールバック（メインスレッドで呼ばれる） |

- 本体未ロード時は、コールバックへ `BankTransactionResult.unavailable()` を即時通知します。

### `asyncTryWithdraw`

```kotlin
fun asyncTryWithdraw(
    uuid: UUID,
    amount: Double,
    note: String,
    displayNote: String,
    callback: Bank.ResultCallback,
)
```

出金を非同期で行い、結果を `callback` へ通知します。引数は `asyncTryDeposit` と同様です。

---

## 非同期 API（互換・非推奨）

```kotlin
@Deprecated // asyncTryDeposit を推奨
fun asyncDeposit(uuid: UUID, amount: Double, note: String, displayNote: String, callback: Bank.ResultTransaction)

@Deprecated // asyncTryWithdraw を推奨
fun asyncWithdraw(uuid: UUID, amount: Double, note: String, displayNote: String, callback: Bank.ResultTransaction)
```

- コールバック型は旧式の [`Bank.ResultTransaction`](#bankresulttransaction)。
- `onResult(resultCode, newBalance)` で通知され、`resultCode` は成功時 `0`、失敗時 `-1`。
- 失敗時の `newBalance` は `0.0`、本体未ロード時も `(-1, 0.0)` を通知します。
- 失敗理由（メッセージ等）は取得できません。

---

## 残高照会

### `getBalance`（同期）

```kotlin
fun getBalance(uuid: UUID): Double
```

- 指定プレイヤーの残高を同期取得します。
- 本体未ロード・取得失敗時は `0.0` を返します（エラーと残高 0 を区別できない点に注意）。
- ⚠️ 内部で `runBlocking` を使用するため、メインスレッドからの呼び出しは非推奨です。

### `asyncGetBalance`（非同期）

```kotlin
fun asyncGetBalance(uuid: UUID, callback: Bank.ResultTransaction)
```

- 残高を非同期取得し、メインスレッドのコールバックへ通知します。
- `resultCode` は成功時 `0`、失敗時 `-1`。失敗・本体未ロード時の `newBalance` は `0.0`。

---

## データ型

### `BankTransactionResult`

入出金 API の実行結果を表すデータクラスです。

```kotlin
data class BankTransactionResult(
    val success: Boolean,
    val balance: Double = 0.0,
    val errorMessage: String? = null,
    val httpStatus: Int? = null,
    val problem: ProblemDetails? = null,
)
```

| プロパティ | 型 | 説明 |
| --- | --- | --- |
| `success` | `Boolean` | 取引が成功したかどうか |
| `balance` | `Double` | 成功時の新残高。失敗時は `0.0` |
| `errorMessage` | `String?` | 失敗理由の表示用メッセージ。成功時は `null` |
| `httpStatus` | `Int?` | API が返した HTTP ステータスコード。取得できない場合は `null` |
| `problem` | `ProblemDetails?` | API が返した `ProblemDetails`。取得できない場合は `null` |

#### 生成用ファクトリ

| メソッド | 説明 |
| --- | --- |
| `BankTransactionResult.success(balance: Double)` | 成功結果を生成 |
| `BankTransactionResult.failure(throwable: Throwable)` | 例外から失敗結果を生成。`ApiHttpException` の場合は `problem.detail` → `problem.title` → `message` の順でメッセージを決定 |
| `BankTransactionResult.unavailable()` | 本体未ロード等で API を利用できない場合の結果（`errorMessage = "Bank APIを利用できません"`） |

### `ProblemDetails`

ASP.NET Core 標準のエラーレスポンスモデル（[ソース](../src/main/java/red/man10/man10bank/api/error/ProblemDetails.kt)）。全フィールド任意。

```kotlin
@Serializable
data class ProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
)
```

| フィールド | 説明 |
| --- | --- |
| `type` | 問題種別を示す URI |
| `title` | 人間可読の概要 |
| `status` | HTTP ステータスコード |
| `detail` | 詳細説明 |
| `instance` | 当該発生個所を示す URI |

### `Bank.ResultCallback`

結果付き非同期 API のコールバック型（SAM / `fun interface`）。

```kotlin
fun interface ResultCallback {
    fun onResult(result: BankTransactionResult)
}
```

### `Bank.ResultTransaction`

旧 API 互換のコールバック型。`resultCode` は成功時 `0`、失敗時 `-1`。

```kotlin
interface ResultTransaction {
    fun onResult(resultCode: Int, newBalance: Double)
}
```

---

## エラーハンドリング

- 内部 HTTP クライアントは非 2xx レスポンスを
  [`ApiHttpException`](../src/main/java/red/man10/man10bank/api/error/ApiHttpException.kt) に正規化します。

  ```kotlin
  class ApiHttpException(
      val status: HttpStatusCode,
      val problem: ProblemDetails? = null,
      message: String,
  ) : Exception(message)
  ```

- 推奨 API（`tryXxx` / `asyncTryXxx`）では、この例外が `BankTransactionResult.failure()` により
  `errorMessage` / `httpStatus` / `problem` へ変換されます。残高不足などの業務エラーもここで判別できます。
- 互換 API では失敗が `Boolean(false)` または `resultCode = -1` としてのみ通知され、詳細は取得できません。
- **本体未ロード時の挙動**:
  - 推奨同期/非同期 API → `BankTransactionResult.unavailable()`
  - `getBalance` → `0.0`
  - 互換非同期 API / `asyncGetBalance` → `onResult(-1, 0.0)`

---

## スレッドモデルと注意点

- **同期 API（`tryXxx` / `getBalance`）** は内部で `runBlocking` を使い HTTP 通信を完了まで待機します。
  メインスレッドで呼ぶとサーバー全体がブロックされるため、可能な限り非同期 API を使用してください。
- **非同期 API（`asyncTryXxx` / `asyncDeposit` / `asyncWithdraw` / `asyncGetBalance`）** は
  `runTaskAsynchronously` で通信を行い、コールバックは `runTask` により**必ずメインスレッド**で呼ばれます。
  そのため、コールバック内から Bukkit API を安全に呼び出せます。
- リクエストには呼び出し元の `plugin.name` と、`Man10Bank` 本体の `serverName`（`config.yml` の `serverName`、
  未設定時は `server.name`）が自動付与されます。

---

## 接続設定（config.yml）

API の接続先・認証・タイムアウトは `Man10Bank` 本体の `config.yml` の `api` セクションで設定します。

| キー | 説明 | 既定値 |
| --- | --- | --- |
| `api.baseUrl` | API のベース URL（必須・空不可） | — |
| `api.apiKey` | Bearer 認証トークン（任意） | なし |
| `api.timeout.requestMs` | リクエストタイムアウト（ms） | `10000` |
| `api.timeout.connectMs` | 接続タイムアウト（ms） | `3000` |
| `api.timeout.socketMs` | ソケットタイムアウト（ms） | `10000` |
| `api.retries` | 失敗時の自動リトライ回数（0〜5 にクランプ） | `2` |

---

## 使用例

### 非同期で出金（推奨）

```kotlin
val bankApi = BankAPI(this)

bankApi.asyncTryWithdraw(player.uniqueId, 1000.0, "shop-purchase", "アイテム購入") { result ->
    // このブロックはメインスレッドで実行される
    if (result.success) {
        player.sendMessage("§a1000円を引き出しました。残高: ${result.balance}円")
    } else {
        player.sendMessage("§c引き出しに失敗しました: ${result.errorMessage}")
    }
}
```

### 非同期で入金（推奨）

```kotlin
bankApi.asyncTryDeposit(player.uniqueId, 500.0, "quest-reward", "クエスト報酬") { result ->
    if (result.success) {
        player.sendMessage("§a500円を入金しました。残高: ${result.balance}円")
    } else {
        player.sendMessage("§c入金に失敗しました: ${result.errorMessage}")
    }
}
```

### 残高を非同期取得

```kotlin
bankApi.asyncGetBalance(player.uniqueId) { code, balance ->
    if (code == 0) {
        player.sendMessage("§a残高: ${balance}円")
    } else {
        player.sendMessage("§c残高の取得に失敗しました")
    }
}
```

### 同期 API（非メインスレッドでの利用を想定）

```kotlin
// 非同期タスク内など、ブロックしても問題ない箇所でのみ使用すること
val result = bankApi.tryWithdraw(player.uniqueId, 1000.0, "batch", "一括処理")
if (!result.success) {
    logger.warning("出金失敗: ${result.errorMessage} (status=${result.httpStatus})")
}
```
