#!/bin/bash
# Spostati nella directory dove risiede lo script
cd "$(dirname "$0")"

# --- CONFIGURAZIONE AMBIENTE ---
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

echo "🚀 Inizio compilazione..."
# Esecuzione Gradle senza daemon per risparmiare RAM in Termux
./gradlew assembleDebug --no-daemon

if [ $? -ne 0 ]; then
    echo "❌ Errore durante la compilazione. L'installazione è stata annullata."
    exit 1
fi

# --- RICERCA APK ---
# Trova l'APK generato (indipendentemente dal nome esatto, prende il primo nel folder debug)
APK_PATH=$(find app/build/outputs/apk/debug -name "*.apk" | head -n 1)

if [ -z "$APK_PATH" ]; then
    echo "❌ Errore: APK non trovato nel percorso app/build/outputs/apk/debug/"
    exit 1
fi

echo "📦 APK trovato: $APK_PATH"

# --- DEPLOY E INSTALLAZIONE ---
echo "🚚 Trasferimento APK in /data/local/tmp..."
# Usiamo un pipe perché rish non ha accesso alla home di Termux
cat "$APK_PATH" | rish -c "cat > '/data/local/tmp/app-debug.apk'"

echo "📲 Installazione in corso..."
# Uso rigoroso di virgolette annidate e flag -r per la reinstallazione
rish -c "pm install -r '/data/local/tmp/app-debug.apk'"

# --- PULIZIA ---
echo "🧹 Pulizia file temporanei..."
rish -c "rm /data/local/tmp/app-debug.apk"

echo "✅ Operazione completata con successo!"
