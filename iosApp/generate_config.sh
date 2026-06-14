#!/bin/bash

# Path to the root .env file
ENV_FILE="../.env"
# Path to Xcode config file
CONFIG_FILE="Configuration/Config.xcconfig"

echo "// Generated dynamically from root .env file" > "$CONFIG_FILE"
echo "TEAM_ID=" >> "$CONFIG_FILE"
echo "BUNDLE_ID=com.fitter.app" >> "$CONFIG_FILE"
echo "APP_NAME=MacroVision" >> "$CONFIG_FILE"

if [ -f "$ENV_FILE" ]; then
    echo "Found .env file at $ENV_FILE. Updating Config.xcconfig..."
    while IFS= read -r line || [ -n "$line" ]; do
        # Strip spaces and carriage returns
        line=$(echo "$line" | tr -d '\r' | xargs)
        
        # Skip empty lines and comments
        if [[ ! "$line" =~ ^# ]] && [[ "$line" =~ = ]]; then
            echo "$line" >> "$CONFIG_FILE"
        fi
    done < "$ENV_FILE"
    echo "Config.xcconfig updated successfully."
else
    echo "Error: .env file not found at $ENV_FILE. Using default configuration."
    echo "OPENROUTER_API_KEY=your_openrouter_api_key_here" >> "$CONFIG_FILE"
    echo "GEMINI_API_KEY=your_gemini_api_key_here" >> "$CONFIG_FILE"
    echo "GROQ_API_KEY=your_groq_api_key_here" >> "$CONFIG_FILE"
fi
