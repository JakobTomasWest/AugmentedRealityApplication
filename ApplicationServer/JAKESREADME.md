# Notes Server with LDAP and JWT

## Overview

This project runs a server with LDAP authentication and JWT-based authorization using Docker Compose.

---

## Prerequisites

- Docker and Docker Compose installed on your system.
- Basic familiarity with command line and Docker.

---

## Running the Server

To start the server and its dependencies, run:

```bash
docker compose up -d --build
```

To stop and remove containers, networks, and volumes:

```bash
docker compose down
```

Check running containers:

```bash
docker compose ps
```

Both `openldap` and `app` containers should be in the `Up` state.

---

## Accessing the Server

- Server (host): [http://localhost:8080](http://localhost:8080)
- Android emulator uses: [http://10.0.2.2:8080](http://10.0.2.2:8080)

---

## Creating Users

You can create users either via the app interface or by seeding LDAP directly.

### Seed a User (example: alex/iscool)

Run the following command to add a user to LDAP:

```bash
docker compose exec -T openldap ldapadd -x -H ldap://localhost:1389 \
-D "cn=admin,dc=ldap,dc=asd,dc=msd,dc=localhost" -w adminpassword <<'LDIF'
dn: cn=alex,dc=ldap,dc=asd,dc=msd,dc=localhost
objectClass: top
objectClass: person
objectClass: organizationalPerson
objectClass: inetOrgPerson
cn: alex
sn: alex
userPassword: iscool
LDIF
```

---

## Verifying User Creation

Check if the user exists in LDAP:

```bash
docker compose exec openldap ldapsearch -x -H ldap://localhost:1389 \
-D "cn=admin,dc=ldap,dc=asd,dc=msd,dc=localhost" -w adminpassword \
-b "dc=ldap,dc=asd,dc=msd,dc=localhost" "(cn=alex)" dn
```

---

## Getting a JWT Token

Use the following command to get a JWT token for a user (example: jake):

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth \
  -H "Content-Type: application/json" \
  -d '{"username":"jake","password":"1"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
echo "$TOKEN"
```

---

## Listing User Uploads

Get uploads scoped to the authenticated user:

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/upload
```

---

## Uploading and Downloading Photos via Curl

- **Upload a photo:**

```bash
curl -X POST http://localhost:8080/api/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/photo.jpg"
```

- **Download a photo:**

```bash
curl -O -J -L -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/upload/{filename}
```

Replace `{filename}` with the actual file name.

---

## Inspecting Files Inside the App Container

To list files inside a user's upload directory (example: jake):

```bash
docker compose exec app ls -al /app/uploads/jake
```

---

## Counting Per-User Uploads

Run this script inside the app container to count files per user:

```bash
docker compose exec app sh -lc '
for d in /app/uploads/*/; do
  [ -d "$d" ] || continue
  u=$(basename "$d")
  c=$(find "$d" -type f | wc -l)
  echo "$u: $c"
done
'
```

---

## Troubleshooting

- Ensure both `openldap` and `app` containers are running.
- Check logs for errors:

```bash
docker compose logs openldap
docker compose logs app
```

- Verify network connectivity between containers.
- Confirm correct LDAP admin password and user credentials.
- Make sure ports 8080 and 1389 are not blocked or used by other services.
- If we don't want to save photos to user locally in /uploads see App volumes in docker-compose.yml

---

This README provides all necessary instructions to run, manage users, and interact with the Notes Server project.
