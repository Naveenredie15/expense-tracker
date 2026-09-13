# CLAUDE.md — Expense Tracker (ICICI, on-device)

Guidance for AI assistants working in this repo. Read this first.

## What this app is
An Android expense-tracker that reads the user's bank SMS **on-device** (no cloud, no AI model)
and shows a dashboard. Scoped to **one user, ICICI bank only, English only**: an ICICI bank
account (UPI) + an ICICI credit card. Parsing is **regex-based**, categorization is
**keyword-based**. There is intentionally **no LLM/AI model** (see decisions below).

Originated as a fork of the MIT-licensed https://github.com/nishanthdn96/expense-tracker
(git remote is `upstream`). Heavily modified since.

## Stack
Kotlin · Jetpack Compose · Material 3 · MVVM · Hilt (DI) · Room (SQLite) · KSP ·
Navigation-Compose · YCharts · kotlinx-datetime. `compileSdk 34`, `minSdk 26`,
package `com.expensetracker`. Build on **JDK 17** (Android Studio bundled JBR).

## Build / run
```bash
./gradlew assembleDebug        # build APK
./gradlew installDebug         # build + install on connected device/emulator
```
- No Android SDK on CI machines by default; real builds happen in Android Studio or via
  `./gradlew` once the SDK is present (`local.properties` has `sdk.dir`).
- Needs a **real device or emulator with SMS** to be useful. On an emulator, send test SMS via
  Extended Controls → Phone → Send Message (sender like `AD-ICICIB`).
- Requires the READ_SMS permission at runtime.

## How parsing works (IMPORTANT — built-in ICICI engine, 3 layers)
`SmsParser` auto-recognizes ICICI's known formats — **the user does not tag formats in the normal
case**. `parseTransaction(sender, message, ts)` runs three layers in order:

1. **Noise filter** — `failedRegex` (failed/declined/cancelled) returns null immediately so a
   non-event is never recorded. `isNoise(message)` (broader: OTP, promo/links, statement-generated,
   reminders/"amount due", "will be debited", collect-requests) is used by the unrecognized scan to
   keep junk out of the tagging inbox. Refund/reversal is NOT noise (it's a real event).
2. **Built-in ICICI extractors** (`extractors` list, first match wins) — shipped regexes for card
   spend, UPI/account debit (`…debited for Rs X; NAME credited`), card-bill payment received,
   account credit (`…credited:Rs X`), generic credit, ATM withdrawal. Then **user templates**
   (per-account overrides). Then a **generic fallback**: any amount+verb SMS on a REGISTERED account
   (matched by last-4) is captured (merchant may be null) so nothing on your own account is missed.
3. Everything is built through one `build(...)` that sets category / card-payment / refund /
   learned-payee (same rules as before). Type = debit if a debit verb appears (debit wins over credit
   for "…debited…; NAME credited").

Validated against real ICICI samples (salary credit, BBPS card-bill payment, card spend, UPI P2P,
ATM, and correctly rejects OTP / statement-generated / declined).

Flow: `SmsReceiver` (live) / `SmsReader` (inbox backfill) → `SmsParser` → `Transaction` → Room →
ViewModels (StateFlow) → Compose UI.

**Tagging is now a rare fallback.** The unrecognized inbox only ever surfaces a *genuine* transaction
SMS the engine couldn't read (has amount + a txn verb, not noise, not parsed) — never statements/OTP/
promos. Profile → "Unrecognized SMS" (`UnrecognizedSmsScreen`) groups them by pattern (signature =
body with digits→`#`). For each, the user picks **Debit / Credit / Promotion**:
- Debit/Credit → tag amount+merchant + account → `TemplateRegexBuilder.build` inserts a per-account
  `SmsTemplate` and re-syncs. (Templates are tried before the generic fallback, so they're overrides.)
- **Promotion** → writes the signature to `ignored_sms_patterns` (entity `IgnoredSmsPattern`) so that
  pattern never surfaces again. The scan filters these out (`IgnoredSmsPatternDao.getSignatures`).
Profile → "SMS formats" edits/deletes saved templates.

## Accounts & formats (decoupled)
Adding an account (`AddAccountScreen`) captures **details only** (last4, label, type, card/salary
config) — NO format tagging in the form. `ProfileViewModel.addAccount` inserts just the `Account`.
On save, the user is routed to **Unrecognized SMS** to tag the formats that actually arrive (see
above). Editing an account (`updateAccount`) changes details only; it re-syncs if `type`/`last4`
changed. Deleting an account deletes its templates + re-syncs.

Formats (`SmsTemplate`s) are managed independently on **Profile → "SMS formats"**
(`SmsFormatsScreen`/`SmsFormatsViewModel`): lists every saved pattern grouped by account, each
editable (re-tag amount/merchant, flip debit/credit — `updateTemplate`) or deletable; both re-sync.
An account can own **any number** of templates.

`Account` fields: `last4`, `label`, `type` (BANK/CARD), and config:
- CARD: `creditLimit`, `statementDay` (bill day), `dueDay`
- BANK: `isSalaryAccount` (when checked, the form asks for `monthlySalary` + `salaryDay`; salaryDay
  starts the Home income/expense cycle — **salary date → next salary date** instead of calendar month
  — and drives the planner's next-salary countdown; monthlySalary drives next-month projection),
  `latestBalance` / `latestBalanceAt` (manually enterable in the account
  form **and** auto-filled from SMS; SMS only overwrites when its reading is *newer* than the stored
  `latestBalanceAt`, so a hand-entered balance isn't clobbered by an older SMS). Manual entry
  re-stamps `latestBalanceAt` to now. This balance drives the planner's disposable-after-bills.

The Home **"Current Balance"** card (replaces the old income−expenses "Net Balance") shows a **live
running balance** = `latestBalance` + net of that bank account's transactions with
`timestamp > latestBalanceAt` (credits +, debits −), via `HomeViewModel.bankBalance`
(`BankBalanceInfo`). So the user enters the balance once (timestamped) and subsequent UPI
debits/credits adjust it; a newer SMS balance resets the base. Carries over prior months' money,
which income−expenses would miss.

Transactions attribute to an account via the matching template's `accountId`.

**Editing:** tapping an account (or its ✏️) opens the same wizard in edit mode
(`AddAccountScreen(accountId=...)`). It pre-fills all fields and re-highlights the tagged
amount/merchant by re-deriving spans from the saved regex (`ProfileViewModel.getAccountEditData` →
`deriveSpans`). Saving calls `updateAccountWithFormats`, which updates the `Account` and **replaces**
both templates. If a parsing-relevant field changed (account `type`, `last4`, or either regex), it
**resyncs and reassigns every transaction**: re-read SMS → `clearTransactionsKeepIgnored()` (wipes
transactions but preserves delete-tombstones) → `insertTransactions`. Adding and deleting an account
also trigger this resync. Cosmetic-only edits (label/limit/dates) skip it.

## Key domain decisions (do not "fix" these without asking)
1. **Transaction identity = UPI RRN when present.** `Transaction.referenceId` holds the UPI RRN
   (`UPI:012345678901`), which is unique per transaction. Dedup + delete-ignore key on it (see
   `TransactionRepository.signatureOf` / `isDuplicate`). Credit-card SMS have **no RRN**, so those
   fall back to `sender|message|timestamp`. This collapses duplicate/differently-worded UPI SMS.
2. **Credit-card "credited" is NOT income.** A CREDIT on a CARD account is a bill payment or a
   refund — never income. Both are `excludeFromTotals = true` (kept out of income/category totals)
   but still shown in the list. They split by intent:
   - **Refund/reversal** (SMS matches `refundRegex`: refund/reversal/reversed) → category **"Refund"**.
     A refund gives money back, so it is **netted against spend**: `HomeViewModel` subtracts
     `getRefundTotalByDateRange` from monthly expenses, and `BillingPlanner.cardStatus` subtracts
     in-cycle refunds from `cycleSpend` (`cycleSpend = debits − refunds`, floored at 0). Since
     `outstanding == cycleSpend`, an in-cycle refund also lowers outstanding. Refund detection also
     applies to **bank** credits (also `excludeFromTotals`, also netted), so a UPI refund nets too.
     **Refund wins over card-payment AND over the failed/declined filter**: a refund on a registered
     card is tagged "Refund" (not "Card Payment") so it nets spend, and a "transaction cancelled …
     Rs X refunded" SMS is still recorded (not dropped as failed). A refund is captured even if its
     card isn't a registered account (generic fallback allows account-less refunds when not noise).
   - **Bill payment** → category **"Card Payment"**. Detected either because the owner is a CARD
     account OR the message itself reads as a card payment (`cardBillPaymentRegex`, e.g. "Payment of
     Rs X received on … Credit Card …") — so it's excluded from income **even if that card isn't a
     registered account**. Kept out of income and
     does not touch spend/expense (the original spends were real). With cycle-based outstanding it no
     longer factors into outstanding.
   Bank credits (salary) are unaffected.
3. **Deleted transactions stay deleted.** Deleting writes a tombstone to `ignored_transactions`
   (keyed by the same signature). Sync skips ignored signatures so they don't reappear.
   **Clear All Transactions** wipes transactions AND the ignore list (a full reset that re-pulls).
4. **No AI model.** Categorization is keyword lists in `TransactionCategorizer.categoryKeywords`
   (edit per category; first match wins; generic keywords only, no personal vendor names). If asked
   for AI: Tier 1 = keywords (current). Tier 2 (if ever) = ~23 MB MiniLM embeddings. Avoid LLMs.
   - **Standardized category set** (default/managed): Food & Dining, Groceries, **Transport**
     (Fuel + Transportation merged), Shopping, Entertainment, Bills & Utilities, Healthcare,
     Education, Investment, Recurring, **Transfer**. Deprecated & removed from the picker: Fuel,
     People, Salary, Refund, Transportation, **Other** (merged into Uncategorized — ATM/cash and
     anything unmatched are just uncategorized). On startup `CategoryRepository.standardizeCategories()`
     deletes deprecated defaults; existing txns migrated via `reassignCategory` (Fuel/Transportation→
     Transport, People→Transfer) and `clearCategory("Other")` → uncategorized.
   - **Transfers.** A UPI **debit** to a named payee with no merchant match → **"Transfer"** (was
     "People"); self/IMPS/NEFT/wallet also → Transfer. Structural (`hasUpiReference && payee != null`),
     not name-based.
   - **Income (salary) is uncategorized** (no "Salary" category) — income never appears in the
     debit-only category breakdown anyway. **"Refund"** is still assigned internally (for spend
     netting) but is not a pickable category.
   - **Per-payee learning (survives re-sync).** When the user re-categorizes a transaction, the
     payee→category mapping is saved in the `payees` table via
     `TransactionRepository.rememberPayeeCategory` (`Payee.linkedCategoryId` holds the category
     **name**). `SmsParser` applies a learned category **above** the keyword/transfer auto-rules (but
     below card-payment/refund). Priority: Card Payment/Refund > learned payee > keyword/transfer.

## Credit-card billing planner
`planner/BillingPlanner.kt` (pure Kotlin, testable) computes, from a card's `statementDay`/`dueDay`:
- current billing cycle (statement day + 1 → next statement day), cycle spend, next due date
- outstanding = **current cycle's net spend** (`== cycleSpend`; prior statements are assumed already
  paid, so earlier spend is treated as zero). available = `creditLimit − outstanding`. (`cardPayments`
  is still passed to `cardStatus` but no longer used for outstanding.)
- salary date = **last working day** of month (skips Sat/Sun; ignores holidays)
- `cycleFor(date, statementDay, dueDay, shift)` — the cycle a spend falls into (a `Cycle` of
  start/end/dueDate), honoring a manual `shift`. Shared by the planner and the transaction dialog.

**Manual billing-cycle override.** Card debits carry `Transaction.billingCycleShift` (0 = the cycle
their date falls in, −1 = previous, +1 = next). The transaction-details dialog shows the cycle for a
card debit and lets the user move it ◀/▶ (banks sometimes post a spend into an adjacent statement).
`PlannerViewModel` buckets each debit by `cycleFor(date, …, shift).end`, so a moved spend counts
toward the chosen cycle's spend. Outstanding is date-independent, so the shift doesn't affect it.

`PlannerViewModel` combines accounts + transactions into `PlannerUiState`; `PlannerSummarySection`
renders the "Planner" card on the Home screen. `UnpaidRecurringSection` (Home, between Current
Balance and Planner) shows a **swipeable `LazyRow` of recurring bills not yet paid this cycle**
(`PlannerUiState.unpaidRecurring` = enabled recurring items with no matching debit since the salary-
cycle start; hidden when all paid). Each unpaid card also shows a **running "Left:"** balance
(current balance − cumulative bills, can go negative/red), passed in from `HomeViewModel.bankBalance`.
The Current Balance card shows **days-to-salary** ("Must last N days") from `BankBalanceInfo.daysToSalary`. Per card: cycle spend, due amount/date, credit-left
(= limit − cycle spend) + usage bar. The "Cash flow" section shows bank balance, next-salary
countdown, and — when a salary account has `monthlySalary` — the salary and **next month
(salary − this cycle's card spend)** = `nextMonthAfterCards`. (Outstanding is no longer shown
separately since it equals cycle spend.)

## Recurring expenses
`RecurringExpense` (name, amount, optional `dayOfMonth`/`category`, `enabled`, plus a **matcher**
`matchRegex`/`matchPayee`) = fixed monthly costs (subscriptions, EMIs, rent) the user enters manually.
Managed on `RecurringExpensesScreen` (Profile → "Recurring expenses").

**Teach-by-example linking + paid/due detection.** When adding one, the dialog lists recent DEBITs of
the entered amount (last 40 days, `candidateTransactions(amount, onlyAmount)`) with a **"Show all
recent"** toggle — so a bill paid at a **different amount** than configured (e.g. ₹10k for a ₹5k item,
refunded later) can still be linked. From the picked txn we derive `matchRegex =
TemplateRegexBuilder.build(msg, emptyList())` and store its `matchPayee`. **Matching requires
amount ≈ item.amount AND (payee contains OR regex)** — so each cycle exactly one same-amount payment
matches (`SmsParser.RecurringMatcher`). Payee matching is contains, either direction.

**Split transaction** (`TransactionDetailsDialog` → "Split Transaction"): split one txn into parts
(amount + label/payee each, must sum to total) — e.g. a ₹10k payment → ₹5k (subscription) + ₹5k
(refundable), so the ₹5k part matches the recurring by amount+payee. Persisted in `transaction_splits`
(keyed by the parent's dedup signature); `TransactionRepository.applySplits`/`expandToParts` re-expand
the original SMS into parts on **every sync**, so splits survive re-syncs. Parts get modified messages
(`… [split n]`) and null `referenceId` for distinct signatures.
`statuses` (combine of items + transactions + accounts) marks each item **Paid ✓/Due** for the
current salary cycle by testing each cycle DEBIT against `matchRegex` (or `matchPayee`). Shown as a
badge per row.

**Recurring is a real category.** A debit matching any recurring item's `matchRegex`/`matchPayee` is
categorized **"Recurring"** by `SmsParser` (matchers loaded in `SmsReader.buildParser` /
`SmsReceiver`; priority Card-Payment/Refund > learned payee > **Recurring** > keyword). Linking an
item also retroactively tags existing matching debits (`tagExistingAsRecurring`). So Insights shows
recurring spend as its own donut slice **from actual transactions** — no planned/actual double-count.
"Recurring" is in the standardized category set. `PlannerViewModel` sums enabled ones (`recurringTotal`) for the Home cash-flow
projection **next month left = salary − card spend − recurring bills** (`nextMonthAfterCards`).

## Insights
`InsightsViewModel` loads all transactions once and computes everything **in-memory**, scoped by
(a) a **time period** (daily…yearly; **Monthly = the salary cycle**, salary date → next salary date,
same as Home, when a salary day is set — else calendar month) and (b) an **account filter** (All +
one chip per account — `setAccountFilter`). The exact window is shown as `periodRangeLabel`
(e.g. "25 Aug – 24 Sep"). Category spend is grouped from period debits (excluding `excludeFromTotals`),
with null/blank category shown as **"Uncategorized"**, rendered as a **donut + legend** (custom
Canvas in `CategoryDonutCard`, no chart lib). Also: income/expenses (refund-netted), savings rate,
top payees, stats, and an income-vs-expense trend across buckets. All respect the account filter.

## Data model / Room
Entities: `Transaction`, `Category`, `Payee`, `Account`, `SmsTemplate`, `IgnoredTransaction`,
`RecurringExpense`, `IgnoredSmsPattern`.
DB `ExpenseTrackerDatabase`, current `version = 13`. **Real migrations now preserve user data** —
each version bump has a `Migration` in the companion object's `ALL_MIGRATIONS` (wired via
`.addMigrations(...)`). `fallbackToDestructiveMigration()` is kept **only as a crash backstop** for
a version gap with no migration. **When you change an entity: (1) bump `version`, and (2) add a
`Migration(old, new)` — an additive `ALTER TABLE ... ADD COLUMN`/`CREATE TABLE` — or that step will
destructively wipe the DB.** For a NOT NULL added column give it `DEFAULT` in the ALTER; do not add
`@ColumnInfo(defaultValue=…)` to the entity without a matching version bump (it changes the schema
hash). DAOs are provided via Hilt in `di/DatabaseModule`. **There must be ONE Room builder:**
`DatabaseModule.provideExpenseTrackerDatabase` delegates to `ExpenseTrackerDatabase.getDatabase()`
(which registers the migrations). Do NOT add a second `Room.databaseBuilder` — a Hilt builder without
`.addMigrations(...)` silently destroys data on every version bump (this bug shipped once).

## Navigation
Bottom nav = 4 tabs: **Home, Transactions, Insights, Profile**. Categories moved **into Profile**
(under "Manage"), reached via a nav row (route still `Screen.Categories.route`). Profile also has
"Recurring expenses" (route `recurring_expenses`), "Unrecognized SMS" (route `unrecognized_sms`,
tag new formats), "SMS formats" (route `sms_formats`, edit/delete saved patterns),
"Clear All Transactions", and the Accounts list;
"Add" opens `add_account` (the wizard).

## Transactions filters
`TransactionsScreen` shows all filters in **one horizontally-scrollable `LazyRow`** (dividers between
groups): **type** (All/Income/Expense — `setTypeFilter`), **account** (All + per account —
`setAccountFilter`), **category** (All / Uncategorized / each — `setCategoryFilter`;
`UNCATEGORIZED` sentinel → `category.isNullOrBlank()`). Chips hidden in multi-select.
`TransactionsViewModel` keeps the full list and applies all filters in-memory (`applyFilters`).
(A salary-cycle month filter exists in the VM — `setMonthFilter` — but is not wired to any UI.)

## Delete UX
- Single: tap a transaction → details dialog → red "Delete Transaction".
- Bulk: long-press → selection mode → bulk delete.
- Both record ignore-tombstones (see decision #3).

## Known gaps / next steps
- **Double-count (bank side):** a bank debit that pays the card bill still counts as an expense
  (the card spends were already counted). Planned fix: a manual "this is a transfer" toggle
  (and/or keyword auto-detect). Not yet implemented.
- "Due next" ≈ current cycle spend; does not subtract a partially-paid prior statement.
- Live SMS via `SmsReceiver` now **parses AND saves** (Hilt `@EntryPoint` → `TransactionRepository`,
  dedup/ignore-aware) and **reassembles multipart SMS** (long ICICI card spends arrive in >1 part;
  the receiver concatenates them before parsing — parsing a single fragment never matches a template).
  It also refreshes the account's latest balance from a live SMS. The Home "Sync" path
  (`SmsReader.readBankSms`) still backfills the inbox.
- Licensing: upstream README says MIT but has no LICENSE file — confirm before monetizing.

## Reference
- `reference/icici-parser-standalone/` — the original pure-JVM parser prototype (not built into app).
- ICICI SMS formats handled (dummy/synthetic samples — use these to test `SmsParser`):
  - UPI/account debit: `ICICI Bank Acct XX000 debited for Rs 100.00 on 01-Jan-25; John Doe credited. UPI:000000000000.`
  - Card spend: `INR 500.00 spent using ICICI Bank Card XX111 on 01-Jan-25 on SAMPLE MERCHANT. Avl Limit: INR 1,00,000.00.`
  - Salary / account credit: `ICICI Bank Account XX000 credited:Rs. 50,000.00 on 31-Jan-25. Info INF*INFT*000000000000*SALARY. Available Balance is Rs. 1,00,000.00.`
  - Card-bill payment (BBPS): `Payment of Rs 10,000.00 has been received on your ICICI Bank Credit Card XX222 through Bharat Bill Payment System on 01-JAN-25.`
  - Correctly REJECTED (noise/failed): OTP, "statement is generated / total amount due",
    "declined/failed", limit-increase promo (`…increasing the limit… SMS CRLIM…`), and failed autopay
    (`…is not debited with Rs… due to cbs rejection…`).
