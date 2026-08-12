#!/data/data/com.termux/files/usr/bin/bash
set -u
cd "$(dirname "$0")" || exit 1

REPO="Sugoroku2"
OWNER="Sekiguchi-Takashi"
MSG="${1:-update}"

TOKEN="$(git config --global github.token)"
if [ -z "$TOKEN" ]; then
  printf '%s\n' "github.token is not set in git config --global"
  exit 1
fi

CODE="$(curl -s -o /dev/null -w '%{http_code}' \
  -X POST \
  -H "Authorization: token ${TOKEN}" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/user/repos \
  -d "{\"name\":\"${REPO}\",\"private\":true}")"
printf '%s\n' "create repo: HTTP ${CODE}"

if [ ! -d .git ]; then
  git init -b main
fi

git config user.name "${OWNER}"
git config user.email "${OWNER}@users.noreply.github.com"

git remote remove origin 2>/dev/null
git remote add origin "https://${OWNER}:${TOKEN}@github.com/${OWNER}/${REPO}.git"

git add -A
git commit -m "${MSG}" || printf '%s\n' "nothing to commit"
git push -u origin main
