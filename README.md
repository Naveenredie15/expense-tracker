# 💰 Expense Tracker — on-device bank-SMS expense tracker

An Android expense tracker that turns your bank SMS into a clean financial dashboard —
**entirely on your device**. No cloud, no account, no AI model, and (by design) **no
internet permission at all**. Your financial data never leaves your phone.

> Built as a personal project to explore modern Android architecture (Jetpack Compose +
> MVVM + Hilt + Room) and the surprisingly deep problem of parsing real-world bank SMS
> reliably with nothing but regex.

Currently tuned for **ICICI Bank** (UPI account + credit card), English SMS. The parsing
engine is modular, so other banks/formats can be added.

---

## 🔒 Privacy by design

This is the whole point of the app:

- **No `INTERNET` permission** in the manifest — the OS makes it *impossible* for the app
  to transmit anything off the device.
- **No cloud, no backend, no analytics, no crash reporting.** Zero third-party network SDKs.
- All parsing is **regex-based**; all categorization is **keyword-based**. No LLM, no
  embeddings, nothing phoning home.
- SMS is read only after you **explicitly grant** the `READ_SMS`/`RECEIVE_SMS` runtime
  permission; the app is fully gated behind it and works with a clear disclosure first.
- Data lives in a local **Room (SQLite)** database only.

---

## ✨ Features

### 📩 Automatic SMS parsing (the core engine)
The app reads your bank SMS and turns each transaction message into a structured record —
no manual entry. Parsing runs in **three layers**, so it's both accurate and extensible:
1. **Noise filter** — throws away OTPs, promotions, "statement generated", "amount due"
   reminders, and failed/declined messages so they never become fake transactions.
2. **Built-in ICICI extractors** — shipped regex patterns recognize card spends, UPI/account
   debits, salary & account credits, credit-card bill payments, ATM withdrawals, and
   refunds/reversals — automatically, with no setup.
3. **User templates + generic fallback** — anything the built-ins miss on one of *your*
   registered accounts is still captured, and you can teach the app new formats yourself.

Works both **live** (a new SMS is parsed and saved the moment it arrives, multi-part SMS
reassembled) and as a **backfill** that scans your existing inbox.

### 🏠 Home dashboard
- **Current Balance** — a *live running balance*: it starts from the latest balance seen in
  an SMS (or one you type in) and then adds/subtracts every later UPI credit/debit, so it
  stays correct between balance SMS.
- **"Must last N days"** — a countdown to your next salary date so you know how long the
  money has to stretch.
- Income vs. expense for the current **salary cycle**, plus a recent-transactions feed.

### 💳 Credit-card billing planner
Understands how credit cards actually bill you:
- Tracks the **current statement cycle**, how much you've spent in it, the **amount due**
  and **due date**, and how much **credit is left** (with a usage bar).
- **Refund-aware** — an in-cycle refund reduces what you owe.
- **Manual cycle nudge** — banks sometimes post a purchase into the next statement; you can
  move any card spend ◀/▶ to the correct cycle.
- Projects **next month's leftover** = salary − this cycle's card spend − recurring bills.

### 🔁 Recurring expenses
- Track fixed monthly costs (subscriptions, EMIs, rent) and see each one marked
  **Paid ✓ / Due** for the current cycle.
- **Teach-by-example** — link a bill to a real past payment and the app learns to recognize
  it every month automatically (even if the amount varies slightly).
- A swipeable row on Home shows **only the bills not yet paid this cycle**, each with a
  running "Left:" balance after that bill.

### ✂️ Transaction splitting
Split a single payment into labelled parts (e.g. a ₹10,000 payment → ₹5,000 subscription +
₹5,000 refundable). Splits **survive re-syncs**, and each part can then match a recurring
bill on its own.

### 📊 Insights
All computed **in-memory** on-device:
- **Category breakdown** as a donut + legend (custom-drawn, no chart library).
- **Income vs. expense trend** over time.
- **Savings rate** and spending stats.
- Everything is filterable by **time period** (daily…yearly, or your salary cycle) and by
  **account**.

### 💼 Multiple accounts
Register your ICICI bank account and credit card separately, each with its own details
(last-4, credit limit, statement/due days, salary config). SMS formats are managed
per-account and independently of account details.

### 🏷️ Smart categorization (no AI)
- Keyword-based categories (Food, Groceries, Transport, Shopping, Bills, etc.) — first match
  wins, fully editable.
- **UPI transfers** to a named person are detected structurally and tagged "Transfer".
- **Per-payee learning** — when you re-categorize a transaction, the app remembers that
  payee's category and applies it on every future sync.

### ➕ Manual entry
Add a transaction by hand (a **`+`** button on the Transactions tab) for spends the bank
never sent an SMS for — e.g. a card payment that only triggered an OTP. Minimal form:
amount + type, with optional description (auto-categorized), account, and date. Manual
entries are flagged and **preserved across re-syncs**.

### 🗂️ Full data control
- Edit and re-categorize any transaction.
- **Delete that stays deleted** — removed transactions won't reappear on the next sync
  (tombstoned), with single and bulk-delete.
- One-tap **full reset** to wipe and re-pull everything.
- Tag or ignore any unrecognized SMS format, and manage all saved formats from Profile.

---

## 🏗️ Architecture

```
SmsReceiver (live) / SmsReader (inbox backfill)
        │
        ▼
    SmsParser  ──►  Transaction  ──►  Room (SQLite)
   (3-layer regex)                         │
                                           ▼
                             ViewModels (StateFlow)  ──►  Compose UI
```

- **Pattern:** MVVM, unidirectional state via `StateFlow`, reactive UI in Compose.
- **DI:** Hilt provides DAOs/repositories; a single Room builder registers real
  data-preserving migrations (`fallbackToDestructiveMigration` kept only as a crash backstop).
- **Parsing is decoupled from UI** and unit-testable; a pure-JVM prototype lives in
  `reference/icici-parser-standalone/`.
- **Domain layer** (`planner/BillingPlanner.kt`) is pure Kotlin — no Android deps —
  so billing-cycle logic is easy to reason about and test.

## 🧰 Tech stack

Kotlin · Jetpack Compose · Material 3 · MVVM · Hilt · Room · KSP · Navigation-Compose ·
kotlinx-datetime · YCharts (+ a custom Canvas donut). `compileSdk 34`, `minSdk 26`.

## 🚀 Build & run

Requires **JDK 17** and the Android SDK (Android Studio bundled JBR works out of the box).

```bash
./gradlew assembleDebug     # build a debug APK
./gradlew installDebug      # build + install on a connected device/emulator
```

To be useful the app needs a real device (or an emulator with SMS). On an emulator, send
test SMS via **Extended Controls → Phone → Send Message** (sender e.g. `AD-ICICIB`). Real
ICICI sample formats to test against are listed in `CLAUDE.md`.

## 📦 Distribution note

Google Play's [SMS/Call Log Permissions policy](https://support.google.com/googleplay/android-developer/answer/10208820)
restricts `READ_SMS`/`RECEIVE_SMS` to a narrow set of app types; a personal expense
tracker does not qualify for the public production track. For personal use / sharing with
friends, sideload the signed APK or use **Firebase App Distribution** — both bypass the
Play policy review entirely.

## 🙏 Credits

Originally forked from [nishanthdn96/expense-tracker](https://github.com/nishanthdn96/expense-tracker)
by Nishanth Duvva, then heavily rewritten (SMS engine, accounts, billing planner, recurring
expenses, splitting, insights, migrations). See [NOTICE](NOTICE).

## 📄 License

[MIT](LICENSE) © 2026 Naveen Reddy.
