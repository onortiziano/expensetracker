# 💰 Expense Tracker

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)]()
[![UI](https://img.shields.io/badge/UI-Material%203-purple.svg)]()

A professional, lightweight, and privacy-focused expense tracking application designed for Android. **Expense Tracker** combines a clean Material 3 interface with powerful data management features, ensuring your financial records are always accurate, customizable, and safe.

## ✨ Key Features

### 🌍 Internationalization & Flexibility
- **Dynamic Currency Support**: Choose your preferred currency symbol ($, €, £, ¥, etc.) to match your local or travel needs.
- **Custom Decimal Separators**: Full control over decimal separators (`,` or `.`), ensuring the app adapts to your regional formatting preferences.
- **Locale-Aware Defaults**: Automatically detects system locale on first launch to provide a seamless "out-of-the-box" experience.

### 🛡️ Robust Data Integrity
- **Atomic Backup & Restore**: Implements a comprehensive backup system that captures the entire SQLite state (including `.db`, `-wal`, and `-shm` files), preventing data loss and ensuring consistency.
- **Local-First Storage**: All your data stays on your device. No cloud accounts, no tracking, total privacy.

### 🎨 Premium Visual Experience
- **Dynamic Icon Styling**: Switch between different Material Design styles (**Filled**, **Outlined**, **Rounded**, **Sharp**, and **TwoTone**) in real-time via the settings menu.
- **Material 3 Design**: Built with the latest Jetpack Compose components for a modern, responsive, and fluid user interface.

## 🛠️ Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
- **Database**: [Room / SQLite](https://developer.android.com/training/data-storage/room)
- **Architecture**: MVVM (Model-View-ViewModel)
- **Dependency Injection**: ViewModelFactory
- **Icons**: Material Icons Extended

## 🚀 Installation

### From F-Droid (Recommended)
1. Install the [F-Droid client](https://f-droid.org/).
2. Search for **Expense Tracker**.
3. Click **Install**.

### From GitHub Releases
1. Go to the [Releases](https://github.com/onortiziano/expensetracker/releases) page.
2. Download the latest `.apk` file.
3. Install it on your Android device (enable "Install from unknown sources" if prompted).

## ⚙️ Configuration

You can customize the app's behavior in the **Settings** screen:
- **Currency**: Change the symbol displayed for all amounts.
- **Separator**: Switch between dot and comma for decimals.
- **Icon Style**: Change the look and feel of the app's iconography.

## 📜 License

This project is licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for more details.

---
*Developed with passion for a cleaner and more organized financial life.*
 
