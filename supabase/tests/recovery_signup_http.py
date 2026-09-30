"""Verify automatic profile creation via real Auth on a disposable recovery stack."""

import json
from pathlib import Path
import subprocess
import sys
import tomllib
import urllib.parse
import urllib.request
import uuid

if not __debug__:
    raise SystemExit("Run with assertions enabled")
project = tomllib.loads((Path(sys.argv[1]) / "supabase/config.toml").read_text())["project_id"]
if project not in {"family-recovery", "family-fresh-bootstrap"}:
    raise SystemExit("Use a disposable family-recovery or family-fresh-bootstrap project")
config = json.loads(subprocess.check_output(
    ["supabase", "status", "--workdir", sys.argv[1], "-o", "json"],
    stderr=subprocess.DEVNULL,
))
base = config["API_URL"]
if urllib.parse.urlparse(base).scheme != "http" or any(
        urllib.parse.urlparse(config[key]).hostname != "127.0.0.1"
       for key in ("API_URL", "DB_URL")):
    raise SystemExit("Only an isolated localhost recovery stack is permitted")


def request(path, token, body=None, method="GET"):
    data = None if body is None else json.dumps(body).encode()
    headers = {"apikey": config["ANON_KEY"], "Authorization": "Bearer " + token,
               "Content-Type": "application/json"}
    with urllib.request.urlopen(urllib.request.Request(
            base + path, data=data, headers=headers, method=method)) as response:
        return json.loads(response.read() or b"null")


buckets = request("/storage/v1/bucket", config["SERVICE_ROLE_KEY"])
assert {bucket["id"] for bucket in buckets if not bucket["public"]} == {
    "avatars", "group-images", "wish-images", "chat-media",
}, "All four recovered media buckets must start private"

email = "recovery-" + uuid.uuid4().hex + "@example.test"
user_id = None
try:
    signup = request("/auth/v1/signup", config["ANON_KEY"], {
        "email": email, "password": "LocalRecoveryFixture123!",
        "data": {"full_name": "Recovery HTTP Fixture", "phone": "fictional",
                 "avatar_color": 3},
    }, "POST")
    user_id = signup["user"]["id"]
    assert signup.get("access_token"), "Local recovery Auth must auto-confirm fixtures"
    rows = request("/rest/v1/users?select=id,auth_id,name,email,mobile,avatar_color",
                   signup["access_token"])
    assert len(rows) == 1, "Signed-in user must see exactly their automatic profile"
    profile = rows[0]
    assert profile["auth_id"] == user_id
    assert profile["id"] != user_id, "Application and Auth IDs must remain distinct"
    assert profile["name"] == "Recovery HTTP Fixture"
    assert profile["email"] == email
    assert profile["mobile"] == "fictional"
    assert profile["avatar_color"] == 3
    print("Recovery signup: 9 real Auth/profile/private-bucket checks passed")
finally:
    if user_id is not None:
        request("/auth/v1/admin/users/" + user_id, config["SERVICE_ROLE_KEY"], method="DELETE")
