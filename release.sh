#!/bin/bash

VERSION=$1

if [ -z "${VERSION}" ];
then
    echo "Usage $0 <version:X.Y.Z>"
    exit 1
fi

echo ${VERSION} >| src/main/resources/version.txt
mvn versions:set -DnewVersion=${VERSION}
rm pom.xml.versionsBackup
git add src/main/resources/version.txt pom.xml
git commit --sign --signoff --message "Incremented version to ${VERSION}"

git tag --message "Release v${VERSION}" --sign "v${VERSION}"

echo "git push && git push :origin && git push --tags"
