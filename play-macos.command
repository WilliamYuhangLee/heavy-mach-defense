#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

# The bundled Gradle 6.7.1 wrapper needs an older JDK than current system Java.
for candidate in /opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home /usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home; do
    if [ -x "$candidate/bin/java" ]; then
        export JAVA_HOME="$candidate"
        break
    fi
done
if [ -z "${candidate:-}" ] || [ ! -x "$candidate/bin/java" ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 11 2>/dev/null) || {
        echo "Java 11 is required. Install it with: brew install openjdk@11"
        exit 1
    }
    export JAVA_HOME
fi

./gradlew :desktop:dist
exec "$JAVA_HOME/bin/java" -XstartOnFirstThread -jar desktop/build/libs/desktop-1.0.jar
