#!/usr/bin/env bash
# Creates the release keystore for Herman Announcer, backs it up to 1Password (vault Development,
# same two-item pattern as the other Android apps), and sets the GitHub Actions secrets.
# Run once. Refuses to overwrite an existing keystore. Never prints the password.
set -euo pipefail
APP="Herman Announcer"
JKS="$HOME/.config/android-signing/herman-announcer.jks"
PASSF="$HOME/.config/android-signing/herman-announcer.pass"
ALIAS="herman"
REPO="My-Weird-Prompts/Herman-Poppleberry-Desktop-Sounds"
[ -e "$JKS" ] && { echo "exists: $JKS"; exit 1; }
mkdir -p "$(dirname "$JKS")"; umask 077
openssl rand -base64 24 | tr -d '/+=\n' > "$PASSF"
PW=$(cat "$PASSF")
keytool -genkeypair -v -keystore "$JKS" -storetype PKCS12 -alias "$ALIAS" -keyalg RSA -keysize 4096 \
  -validity 10000 -storepass "$PW" -keypass "$PW" \
  -dname "CN=Herman Announcer, O=My Weird Prompts" >/dev/null
# 1Password first: Document (keystore) + Password item.
op document create "$JKS" --title "Android signing key - $APP" --vault Development \
  --file-name herman-announcer.jks >/dev/null
op item create --category password --title "Android signing key - $APP - password" --vault Development \
  "password=$PW" "notesPlain=alias=$ALIAS; keystore: document 'Android signing key - $APP'; app android/ in $REPO" >/dev/null
# Then GitHub Actions secrets.
base64 -w0 "$JKS" | gh secret set SIGNING_KEYSTORE_B64 -R "$REPO"
printf %s "$PW"    | gh secret set SIGNING_STORE_PASSWORD -R "$REPO"
printf %s "$PW"    | gh secret set SIGNING_KEY_PASSWORD -R "$REPO"
printf %s "$ALIAS" | gh secret set SIGNING_KEY_ALIAS -R "$REPO"
echo "done: $JKS"
