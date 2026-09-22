#!/bin/bash

# Generates iosApp/Configuration/Config.xcconfig from root .env file.
# SECURITY: Only GATEWAY_URL, SUPABASE_URL, SUPABASE_ANON_KEY, TEAM_ID, BUNDLE_ID, APP_NAME
# are written to the xcconfig. VLM API keys (GEMINI, OPENROUTER, GROQ) are NEVER included —
# all AI inference routes through the Cloudflare Worker gateway.

ENV_FILE="../.env"
CONFIG_FILE="Configuration/Config.xcconfig"

echo "// Generated dynamically from root .env file" > "$CONFIG_FILE"
echo "// ZERO VLM API keys here — all AI inference routes through the Cloudflare Worker" >> "$CONFIG_FILE"
echo "TEAM_ID=" >> "$CONFIG_FILE"
echo "BUNDLE_ID=com.fitter.app" >> "$CONFIG_FILE"
echo "APP_NAME=Fitter" >> "$CONFIG_FILE"

# Allowed keys to propagate to iOS config (allowlist approach)
ALLOWED_KEYS=("GATEWAY_URL" "SUPABASE_URL" "SUPABASE_ANON_KEY")

if [ -f "$ENV_FILE" ]; then
    echo "Found .env file at $ENV_FILE. Updating Config.xcconfig (allowlist only)..."
    while IFS= read -r line || [ -n "$line" ]; do
        # Strip carriage returns + leading/trailing spaces
        line=$(echo "$line" | tr -d '\r' | xargs)

        # Skip empty lines and comments
        if [[ "$line" =~ ^# ]] || [[ ! "$line" =~ = ]]; then
            continue
        fi

        # Extract the key name (left of first =)
        key="${line%%=*}"

        # Only write allowed keys — never VLM keys
        for allowed in "${ALLOWED_KEYS[@]}"; do
            if [ "$key" = "$allowed" ]; then
                echo "$line" >> "$CONFIG_FILE"
                break
            fi
        done
    done < "$ENV_FILE"
    echo "Config.xcconfig updated successfully (allowlist: ${ALLOWED_KEYS[*]})."
else
    echo "Warning: .env file not found at $ENV_FILE. Using defaults."
    echo "GATEWAY_URL=https://fitter-gateway.workers.dev" >> "$CONFIG_FILE"
    echo "SUPABASE_URL=https://placeholder-project.supabase.co" >> "$CONFIG_FILE"
    echo "SUPABASE_ANON_KEY=" >> "$CONFIG_FILE"
fi
