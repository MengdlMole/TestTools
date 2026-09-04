#!/bin/sh
set -eu

MAVEN_VERSION=3.9.11
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MAVEN_HOME_DIR="$PROJECT_DIR/.mvn/apache-maven-$MAVEN_VERSION"
ARCHIVE="$PROJECT_DIR/.mvn/apache-maven-$MAVEN_VERSION-bin.tar.gz"
BASE_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION"

if [ ! -x "$MAVEN_HOME_DIR/bin/mvn" ]; then
  mkdir -p "$PROJECT_DIR/.mvn"
  echo "Downloading Apache Maven $MAVEN_VERSION..." >&2
  curl -fsSL "$BASE_URL/apache-maven-$MAVEN_VERSION-bin.tar.gz" -o "$ARCHIVE"
  curl -fsSL "$BASE_URL/apache-maven-$MAVEN_VERSION-bin.tar.gz.sha512" -o "$ARCHIVE.sha512"
  EXPECTED=$(tr -d '[:space:]' < "$ARCHIVE.sha512")
  ACTUAL=$(shasum -a 512 "$ARCHIVE" | awk '{print $1}')
  if [ "$EXPECTED" != "$ACTUAL" ]; then
    echo "Maven archive checksum verification failed" >&2
    exit 1
  fi
  tar -xzf "$ARCHIVE" -C "$PROJECT_DIR/.mvn"
fi

exec "$MAVEN_HOME_DIR/bin/mvn" "$@"
