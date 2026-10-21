#!/bin/sh
# Minimal POSIX Gradle Wrapper Forwarder
DIRname="$(dirname "$0")"
if [ -x "$DIRname/gradle/wrapper/gradle-wrapper.jar" ]; then
    exec java -jar "$DIRname/gradle/wrapper/gradle-wrapper.jar" "$@"
else
    exec gradle "$@"
fi