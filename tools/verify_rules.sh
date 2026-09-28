#!/bin/bash
# Functional verification of firestore.rules against the live project.
# Uses REAL uids from signup and omits the auth header for public reads.
set -u
PROJECT="moviepox-7ab66"
APIKEY="AIzaSyBe6o6WksqpwFCDE-OkGuH1OuSyn9rjULc"
BASE="https://firestore.googleapis.com/v1/projects/$PROJECT/databases/(default)/documents"
OUT="verify_results.txt"
: > "$OUT"

signup() {
  curl -s -X POST "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$APIKEY" \
    -H "Content-Type: application/json" -d '{"returnSecureToken":true}'
}
R1=$(signup); U1=$(echo "$R1" | python -c "import json,sys; d=json.load(sys.stdin); print(d.get('idToken',''))" 2>/dev/null)
A1=$(echo "$R1" | python -c "import json,sys; d=json.load(sys.stdin); print(d.get('localId',''))" 2>/dev/null)
R2=$(signup); U2=$(echo "$R2" | python -c "import json,sys; d=json.load(sys.stdin); print(d.get('idToken',''))" 2>/dev/null)
A2=$(echo "$R2" | python -c "import json,sys; d=json.load(sys.stdin); print(d.get('localId',''))" 2>/dev/null)
if [ -z "$U1" ] || [ -z "$U2" ]; then
  echo "FAIL anonymous signup - is the Anonymous provider enabled?" >> "$OUT"
  cat "$OUT"; exit 1
fi
echo "signed in 2 anonymous users (${#A1} / ${#A2} char uids)" >> "$OUT"
RUNID=$(date +%s%N | tail -c 8)  # unique per run: no doc-ID collisions

w()   { curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE/$2" -H "Authorization: Bearer $1" -H "Content-Type: application/json" -d "$3"; }
wm()  { curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE/$2?updateMask.fieldPaths=$4" -H "Authorization: Bearer $1" -H "Content-Type: application/json" -d "$3"; }
rd()  { curl -s -o /dev/null -w "%{http_code}" "$BASE/$2" -H "Authorization: Bearer $1"; }
rdpub() { curl -s -o /dev/null -w "%{http_code}" "$BASE/$1"; }  # no auth header at all
del() { curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE/$2" -H "Authorization: Bearer $1"; }

check() { if [ "$2" = "$3" ]; then echo "PASS $1 ($3)" >> "$OUT"; else echo "FAIL $1 - expected $2 got $3" >> "$OUT"; fi; }

# Own-data access (real uid in path)
check "own watchlist write" 200 "$(w "$U1" "users/$A1/watchlist/movie-550" '{"fields":{"titleName":{"stringValue":"Fight Club"}}}')"
check "own watchlist read" 200 "$(rd "$U1" "users/$A1/watchlist/movie-550")"
check "cross-user read denied" 403 "$(rd "$U2" "users/$A1/watchlist/movie-550")"
check "cross-user write denied" 403 "$(w "$U2" "users/$A1/watchlist/movie-550" '{"fields":{"titleName":{"stringValue":"hacked"}}}')"
# Followers create notifications in someone else's space (like the app does).
check "notification write by follower (app-shaped)" 200 "$(w "$U2" "users/$A1/notifications/n$RUNID" '{"fields":{"uid":{"stringValue":"'"$A2"'"},"type":{"stringValue":"new_follower"},"text":{"stringValue":"started following you"},"read":{"booleanValue":false}}}')"
check "notification spam write denied" 403 "$(w "$U2" "users/$A1/notifications/n2$RUNID" '{"fields":{"uid":{"stringValue":"'"$A2"'"},"type":{"stringValue":"new_follower"},"text":{"stringValue":"'"$(python -c "print('x'*500)")"'"},"read":{"booleanValue":false}}}')"
check "notification self-mark-read (owner)" 200 "$(wm "$U1" "users/$A1/notifications/n$RUNID" '{"fields":{"read":{"booleanValue":true}}}' "read")"

# Reviews: public read, owner-only write
check "review create (owner uid)" 200 "$(w "$U1" "reviews/r$RUNID" '{"fields":{"uid":{"stringValue":"'"$A1"'"},"key":{"stringValue":"movie-550"},"text":{"stringValue":"great movie"},"authorName":{"stringValue":"A"}}}')"
check "profile public read" 200 "$(rdpub "profiles/$A1")"
check "review public read" 200 "$(rdpub "reviews/r$RUNID")"
check "review forgery denied" 403 "$(w "$U2" "reviews/rf$RUNID" '{"fields":{"uid":{"stringValue":"'"$A1"'"},"text":{"stringValue":"fake"}}}')"
check "review foreign edit denied" 403 "$(w "$U2" "reviews/r$RUNID" '{"fields":{"uid":{"stringValue":"'"$A1"'"},"text":{"stringValue":"vandalized"}}}')"

# Profiles: public read, owner write
check "profile owner write" 200 "$(w "$U1" "profiles/$A1" '{"fields":{"uid":{"stringValue":"'"$A1"'"},"name":{"stringValue":"A"}}}')"
check "profile public read" 200 "$(rdpub "profiles/$A1")"
check "profile foreign write denied" 403 "$(w "$U2" "profiles/$A1" '{"fields":{"uid":{"stringValue":"'"$A1"'"},"name":{"stringValue":"nope"}}}')"

# Watch parties: create/join/chat, host-only playback
check "party create (host)" 200 "$(w "$U1" "watch_parties/T$RUNID" '{"fields":{"hostUid":{"stringValue":"'"$A1"'"},"code":{"stringValue":"TESTZZ"},"participants":{"mapValue":{"fields":{"'"$A1"'":{"stringValue":"A"}}}}}}')"
check "party read (signed-in)" 200 "$(rd "$U2" "watch_parties/T$RUNID")"
check "party join (participants merge, app-shaped)" 200 "$(wm "$U2" "watch_parties/T$RUNID" '{"fields":{"participants":{"mapValue":{"fields":{"'"$A2"'":{"stringValue":"B"}}}}}}' "participants")"
check "party foreign playback denied" 403 "$(w "$U2" "watch_parties/T$RUNID" '{"fields":{"playing":{"booleanValue":true},"positionSeconds":{"integerValue":"42"}}}')"
check "chat create (own uid)" 200 "$(w "$U2" "watch_parties/T$RUNID/messages/m$RUNID" '{"fields":{"uid":{"stringValue":"'"$A2"'"},"name":{"stringValue":"B"},"text":{"stringValue":"hi"}}}')"
check "chat public read (signed-in)" 200 "$(rd "$U1" "watch_parties/T$RUNID/messages/m$RUNID")"

# Default deny
check "default deny unknown collection" 403 "$(w "$U1" "anything/else" '{"fields":{"a":{"stringValue":"b"}}}')"

# Cleanup
del "$U1" "users/$A1/notifications/n$RUNID" >/dev/null
del "$U1" "users/$A1/watchlist/movie-550" >/dev/null
del "$U1" "reviews/r$RUNID" >/dev/null
del "$U1" "profiles/$A1" >/dev/null
del "$U1" "watch_parties/T$RUNID" >/dev/null
echo "cleanup done" >> "$OUT"

cat "$OUT"
