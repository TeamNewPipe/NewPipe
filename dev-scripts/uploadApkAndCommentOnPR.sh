#!/usr/bin/env bash

if [ "$TRAVIS_PULL_REQUEST" = "false" ]; then
    exit 0
else
    COMMIT_HASH=$(git rev-parse --short HEAD)
    APK_NAME="pr${TRAVIS_PULL_REQUEST}-${COMMIT_HASH}.apk"
    FILES_DIR="/tmp/newpipe-files"
    DATE="$(date +"%Y/%m/%d")"
    APK_DIR="${FILES_DIR}/${DATE}"
    git clone https://github.com/newpipe-machine-user/newpipe-files.git "${FILES_DIR}"
    mkdir -p "${APK_DIR}"
    cp ./app/build/outputs/apk/debug/app-debug.apk "${APK_DIR}/${APK_NAME}"
    cd "${FILES_DIR}" && git add -A && git commit -m "${APK_NAME}" && git push origin master
    APK_LINK="https://github.com/newpipe-machine-user/newpipe-files/blob/master/${DATE}/${APK_NAME}"
    COMMENT="Find the test apk [${APK_NAME}](${APK_LINK})\n <sub>TeamNewPipe takes no responsibility for this apk. Install at your own risk</sub>"
    curl -nso /dev/null -X POST -d "{\"body\": \"${COMMENT}\"}" "https://api.github.com/repos/${TRAVIS_REPO_SLUG}/issues/${TRAVIS_PULL_REQUEST}/comments"
fi
