# 💰 Expense Tracker

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)]()
[![UI](https://img.shields.io/badge/UI-Material%203-purple.svg)]()

A professional, lightweight, and privacy-focused expense tracking application designed for Android. **Expense Tracker** combines a clean Material 3 interface with powerful data management features, ensuring your financial records are always accurate, customizable, and safe.

## ✨ Key Features

### 📊 Smart Classification & Budgeting
- **Hierarchical Categories**: Organize expenses with a Main Category $\rightarrow$ Sub-Category structure for granular and intuitive tracking.
- **Global & Category Budgets**: Set financial limits both globally and per category, with support for historical planning for specific months and years.
- **Visual Analytics**: Dedicated analytics screen with a custom bar chart to track 'Budget vs Actual' spending, providing a clear overview of financial health.
- **Precise Temporal Tracking**: Integrated DatePicker for transactions, ensuring spending is mapped to the correct budget period.
- **Advanced Tagging**: Assign multiple custom tags to transactions for cross-category analysis, featuring a compact and intuitive "2 + n" visualization.
- **Surgical Transaction Editing**: Complete control over transaction details, including optional notes and a refined editing workflow.

### 🌍 Internationalization & Flexibility
- **Full UI Translation (Italian & English)**: The entire interface is translated and externalized through Android string resources — the UI automatically follows your system language.
- **Runtime Language Selector**: Choose **System**, **Italiano** or **English** directly from the app; the selection is applied instantly (with an automatic app restart on devices where system APIs are not enough).
- **Dynamic Currency Support**: Choose your preferred currency symbol ($, €, £, ¥, etc.) to match your local or travel needs.
- **Custom Decimal Separators**: Full control over decimal separators (`,` or `.`), ensuring the app adapts to your regional formatting preferences.

### 🕐 Recurring Transactions
- **Recurring Transactions**: define transactions that repeat on a fixed frequency (DAILY, WEEKLY, BIWEEKLY, MONTHLY, ANNUAL) with optional end date and category, in addition to tags.
- **Automatic Generation**: recurring instances are materialized as normal transactions directly into the database; next-due dates and advances are computed calendar-aware.
- **Scheduling & Reminders**: on app start, due recurring instances are generated and a system reminder is scheduled; overdue reminders are emitted and the app supervises any schedule changes.
- **Management Screen**: dedicated screen with a due-dates calendar, list of recurring items (add via FAB, tap to edit, long-press/settings for edit, swipe-to-delete on either side), pause/resume and tags/category support.

### 💸 Split Expenses & Credits
- **Two Ways to Split**: record a group expense from the *New transaction* screen in two modes — **A "Everyone pays their share"** saves a single transaction with only your share (total ÷ participants, rounded half-even so the sum never drifts) plus a note showing the split, or **B1 "I pay for everyone"** saves the full amount as an expense and automatically creates a debt for each other participant.
- **Automatic Debts**: each participant is stored as a personal debt; the expense and its debts are written atomically in a single database transaction, so they can never be out of sync.
- **Credits Screen**: a dedicated screen in the navigation drawer lists all open debts with their remaining balances; tapping a debt lets you confirm the reimbursement, which atomically marks it as settled and books an automatic **income** transaction (the credit return) — no double-income and no data loss.
- **Rich Transaction Dates**: every transaction now shows its date on the Home list, in the history and in the detail screen, formatted with your system locale.

### ⚙️ Organized Settings
- The **Settings** screen is split into dedicated sub-screens for a cleaner experience: **Visual Preferences**, **Budget Management**, **Category/Tag Management** and **Data Management (Backup)**.

### 🛡️ Robust Data Integrity
- **Atomic Backup & Restore**: Implements a comprehensive backup system that captures the entire SQLite state (including `.db`, `-wal`, and `-shm` files), preventing data loss and ensuring consistency.
- **Local-First Storage**: All your data stays on your device. No cloud accounts, no tracking, total privacy.

### 🎨 Premium Visual Experience
- **Dynamic Icon Styling**: Switch between different Material Design styles (**Filled**, **Outlined**, **Rounded**, **Sharp**, and **TwoTone**) in real-time via the settings menu.
- **Material 3 Design**: Built with the latest Jetpack Compose components for a modern, responsive, and fluid user interface.

## 🔌 External Integration

### Intent-based Expense Ingestion
- **Zero-Footprint Entry**: Other apps (or automation tools) can insert expenses directly into the database without opening Expense Tracker, with negligible battery consumption.
- **Two Equivalent Entry Points**:
  - **BroadcastReceiver** — send an explicit broadcast with action `it.ciano.expensetracker.ADD_EXPENSE`.
  - **Deep Link** — launch the transparent Activity via URI `expensetracker://add_expense?amount=12.50`.
- **Supported Parameters**: `amount` (required), `category` (defaults to "Varie"), `note`/`description`, `date` (ISO-8601 or epoch millis).
- **Auto-Creation**: if the specified category does not exist, it is created automatically.

#### Quick test (adb)

```bash
# Minimal expense via broadcast
adb shell am broadcast -a it.ciano.expensetracker.ADD_EXPENSE \
  -p it.ciano.expensetracker --es amount "12.50"

# Full expense via deep link
adb shell am start -a android.intent.action.VIEW \
  -d "expensetracker://add_expense?amount=12.50&category=Pranzo&note=Ristorante&date=2026-08-13"
```

> See [TESTING_GUIDE.md](TESTING_GUIDE.md) for detailed usage, parameters, and known limitations.

## 📷 Receipt OCR (Camera)

- **One-Tap Ingestion**: open the "New transaction" screen and tap the camera icon in the top bar — the form is filled automatically.
- **On-Device Recognition**: Google ML Kit text recognition runs entirely on the device — the photo never leaves your phone.
- **Smart Auto-Fill**: amount, date, merchant and a suggested category are parsed from the receipt text and pre-filled in the transaction form; every field stays editable.
- **Preprocessing Pipeline**: image preprocessing with priority to grayscale plus Otsu binarization improves OCR accuracy on thermal-paper receipts and fixes the green-tint issue on some MIUI devices.
- **Accurate Merchant Detection**: the parser searches for Italian company/legal suffixes (`S.R.L.`, `S.P.A.`, `S.N.C.`, `S.A.S.`, `S.A.P.A.`, `S.S.`, `S.C. A R.L.`, `S.C.R.L.`, `S.C.S.`, `S.T.S.`, `S.T.P.`, `S.R.L.S.`, *Ditta Individuale*) and picks that line as the merchant, falling back to the first valid receipt line when no suffix is found.
- **Guided Flow**: after the shot you confirm the photo, then OCR and parsing run on the next screen; a dedicated diagnostic screen helps verify the capture → recognition pipeline.

## 📥 CSV / OFX Import

- **Manual Import**: select a `CSV`, `TSV` or `OFX` file from your device (Storage Access Framework); the app never auto-syncs — import is always manual and local-first.
- **Column Mapping Screen**: for CSV/TSV/TXT a dedicated mapping step runs between file selection and preview: you match file columns to app fields (Date, Description, Amount, Category, Type). Every bank/app has its own layout, so nothing is forced; only **Amount** is mandatory — a missing date falls back to today and a missing description to the placeholder *"Importato da file"*.
- **Type Selection**: in the mapping screen you choose how income/expense is derived — *all as expenses*, *all as income*, *from the sign/value* (negative → expense, positive → income), or from a dedicated type column when the file has one.
- **Robust Parsing**: headers are auto-detected in Italian and English (`Data operazione`, `Importo ( € )`, `Descrizione`, …); dates support ISO, `dd/MM/yyyy`, `MM/dd/yyyy` and Italian month names (`20 lug 2026, 07:00`); amounts accept mixed separators — the last `,`/`.` followed by 1–2 digits is the decimal (`203.80` = 203,80 €) while 3+ digits are thousands (`1.234` = 1234); row endings `\r`, `\n` and `\r\n` are all handled.
- **OFX**: legacy SGML (pre-2000) and modern XML (v1.x / v2.x) for bank statement exports — parsed with the platform XML engine, no cloud APIs.
- **Preview & Categories**: before saving you can review every parsed row, assign a per-row category (or create a new one inline), and even apply one category to all rows from the same file category.
- **Row-level errors**: rows with an invalid date or amount are skipped and reported, without blocking the rest of the import.
- **Deduplication**: the "Skip duplicates" toggle (ON by default) avoids re-importing transactions that already exist with the same title, amount, date, and type.
- **Atomic Insert**: imported transactions are written in a single database transaction — nothing is partially imported.

## 🛠️ Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
- **Database**: [Room / SQLite](https://developer.android.com/training/data-storage/room)
- **Architecture**: MVVM (Model-View-ViewModel)
- **Dependency Injection**: ViewModelFactory
- **Icons**: Material Icons Extended

## 🚀 Installation

### From GitHub Releases
1. Go to the [Releases](https://github.com/onortiziano/expensetracker/releases) page.
2. Download the latest `.apk` file.
3. Install it on your Android device (enable "Install from unknown sources" if prompted).

## ⚙️ Configuration

You can customize the app's behavior in the **Settings** screen, organized into sub-screens:
- **Visual Preferences**: language (System / Italiano / English), currency symbol, decimal separator, and icon style.
- **Budget Management**: set the total monthly budget and open the budget analytics.
- **Category/Tag Management**: manage expense categories and tags.
- **Data Management**: backup and restore your data.

## 📜 License

This project is licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for more details.

---

## 🇬🇧 Support / 🇮🇹 Supporto

If you like this project and want to help keep it active, you can offer me a coffee or make a small donation.

Se ti piace questo progetto e vuoi aiutarmi a mantenerlo attivo, puoi offrirmi un caffè o una piccola donazione.

[![Donate with PayPal](https://img.shields.io/badge/Donate-PayPal-00457C?style=for-the-badge&logo=paypal)](https://paypal.me/onortiziano)

---
*Developed with passion for a cleaner and more organized financial life.*
