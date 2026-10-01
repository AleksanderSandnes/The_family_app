"""Real HTTP smoke tests for a disposable localhost Supabase stack.

Requires the captured schema and staged function/storage policies. Creates only
fictional fixtures, flips only LOCAL buckets private, and leaves fixtures in this
disposable database. Never run against a shared or production database.
"""

import argparse
import json
from pathlib import Path
import subprocess
import time
import tomllib
import urllib.error
import urllib.parse
import urllib.request
import uuid

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--workdir", required=True)
args = parser.parse_args()
project_id = tomllib.loads((Path(args.workdir) / "supabase/config.toml").read_text())[
    "project_id"
]
if project_id not in {"family-media-db", "family-db-validation"}:
    raise SystemExit("Use a disposable family-media-db or family-db-validation project")
if not __debug__:
    raise SystemExit("Run with assertions enabled")
container = "supabase_db_" + project_id
cfg = json.loads(
    subprocess.check_output(
        ["supabase", "status", "--workdir", args.workdir, "-o", "json"],
        stderr=subprocess.DEVNULL,
    )
)
base = cfg["API_URL"]
endpoint = urllib.parse.urlparse(base)
if (
    endpoint.scheme != "http"
    or endpoint.hostname != "127.0.0.1"
    or endpoint.username is not None
    or endpoint.password is not None
    or urllib.parse.urlparse(cfg["DB_URL"]).hostname != "127.0.0.1"
):
    raise SystemExit(
        "Only the disposable localhost Auth/Storage/database stack is permitted"
    )
checks = 0


def sql(statement):
    subprocess.run(
        [
            "docker",
            "exec",
            "-i",
            container,
            "psql",
            "-U",
            "postgres",
            "-d",
            "postgres",
            "-v",
            "ON_ERROR_STOP=1",
        ],
        input=statement.encode(),
        check=True,
        stdout=subprocess.DEVNULL,
    )


def request(
    method, path, token=None, body=None, content="application/json", extra=None
):
    headers = {"apikey": cfg["ANON_KEY"]}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        headers["Content-Type"] = content
        if not isinstance(body, bytes):
            body = json.dumps(body).encode()
    headers.update(extra or {})
    req = urllib.request.Request(base + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()


def success(result):
    global checks
    status, body = result
    assert 200 <= status < 300, (status, body[:200])
    checks += 1
    return body


def denied(result):
    global checks
    status, body = result
    assert 400 <= status < 500, (status, body[:200])
    checks += 1


users = []
for _ in range(2):
    email = "media-" + uuid.uuid4().hex + "@example.test"
    password = uuid.uuid4().hex + "Ab1!"
    user = json.loads(
        success(
            request(
                "POST",
                "/auth/v1/admin/users",
                cfg["SERVICE_ROLE_KEY"],
                {"email": email, "password": password, "email_confirm": True},
            )
        )
    )
    aid = str(uuid.UUID(user["id"]))
    app = str(uuid.uuid4())
    fam = str(uuid.uuid4())
    conv = str(uuid.uuid4())
    wl = str(uuid.uuid4())
    session = json.loads(
        success(
            request(
                "POST",
                "/auth/v1/token?grant_type=password",
                cfg["ANON_KEY"],
                {"email": email, "password": password},
            )
        )
    )
    users.append(
        dict(aid=aid, app=app, fam=fam, conv=conv, wl=wl, token=session["access_token"])
    )
    sql(f"""
        insert into public.users(id,auth_id,name,email)
          values('{app}','{aid}','Fictional media user','{email}');
        insert into public.families(id,name,join_code,admin_id)
          values('{fam}','Fictional media family {fam}','{fam}','{app}');
        update public.users set family_id='{fam}' where id='{app}';
        insert into public.conversations(id,user_from,family_id)
          values('{conv}','{app}','{fam}');
        insert into public.conversation_participants(conversation_id,user_id)
          values('{conv}','{app}');
        insert into public.wishlists(id,owner_user_id,family_id,name)
          values('{wl}','{app}','{fam}','Fictional wishlist');
        """)
a, b = users
sql(
    "update storage.buckets set public=false where id in ('avatars','wish-images','group-images','chat-media');"
)
objects = [
    ("avatars", a["aid"] + "/http.jpg"),
    ("wish-images", a["app"] + "/http.jpg"),
    ("group-images", "family-photos/" + a["fam"] + "/http.jpg"),
    ("group-images", a["conv"] + "/http.jpg"),
    ("chat-media", a["conv"] + "/" + a["aid"] + "/http.m4a"),
]
pixels = b"fictional-private-media"
for bucket, path in objects:
    print("Checking private bucket:", bucket, flush=True)
    object_path = "/storage/v1/object/" + bucket + "/" + path
    success(
        request(
            "POST",
            object_path,
            a["token"],
            pixels,
            "application/octet-stream",
            {"x-upsert": "true"},
        )
    )
    success(
        request(
            "POST",
            object_path,
            a["token"],
            pixels,
            "application/octet-stream",
            {"x-upsert": "true"},
        )
    )
    assert (
        success(
            request(
                "GET",
                "/storage/v1/object/authenticated/" + bucket + "/" + path,
                a["token"],
            )
        )
        == pixels
    )
    signed = json.loads(
        success(
            request(
                "POST",
                "/storage/v1/object/sign/" + bucket + "/" + path,
                a["token"],
                {"expiresIn": 300},
            )
        )
    )["signedURL"]
    assert success(request("GET", "/storage/v1" + signed)) == pixels
    denied(request("GET", "/storage/v1/object/public/" + bucket + "/" + path))
    for token in [b["token"], cfg["ANON_KEY"]]:
        denied(
            request(
                "POST",
                "/storage/v1/object/sign/" + bucket + "/" + path,
                token,
                {"expiresIn": 300},
            )
        )
        denied(
            request(
                "GET", "/storage/v1/object/authenticated/" + bucket + "/" + path, token
            )
        )
    denied(
        request(
            "POST",
            object_path,
            b["token"],
            b"overwrite",
            "application/octet-stream",
            {"x-upsert": "true"},
        )
    )
# Sharing authorizes only the referenced image, and revocation blocks new links.
wpath = a["app"] + "/http.jpg"
sql(
    f"insert into public.wishes(wishlist_id,user_id,text,image_url) values('{a['wl']}','{a['app']}','Fictional wish','https://bntcznvsbyshetndbxfa.supabase.co/storage/v1/object/public/wish-images/{wpath}'); insert into public.wishlist_shares(wishlist_id,user_id) values('{a['wl']}','{b['app']}');"
)
signed = json.loads(
    success(
        request(
            "POST",
            "/storage/v1/object/sign/wish-images/" + wpath,
            b["token"],
            {"expiresIn": 300},
        )
    )
)["signedURL"]
assert success(request("GET", "/storage/v1" + signed)) == pixels
sql(f"delete from public.wishlist_shares where wishlist_id='{a['wl']}';")
denied(
    request(
        "POST",
        "/storage/v1/object/sign/wish-images/" + wpath,
        b["token"],
        {"expiresIn": 300},
    )
)
# A capability already issued remains valid until expiry (documented limitation).
assert success(request("GET", "/storage/v1" + signed)) == pixels
short = json.loads(
    success(
        request(
            "POST",
            "/storage/v1/object/sign/wish-images/" + wpath,
            a["token"],
            {"expiresIn": 1},
        )
    )
)["signedURL"]
time.sleep(3)
denied(request("GET", "/storage/v1" + short))
print(
    f"PASS: {checks} real Auth/Storage HTTP assertions across all four private buckets; upload/upsert, signing/download, denial, share revocation and expiry."
)
