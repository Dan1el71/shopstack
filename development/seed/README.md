# Demo data

`seed-demo.sh` loads a catalogue of six products and two accounts into a running
development stack:

| Account | Password | Role |
|---|---|---|
| `seller` | `Demo12345!` | SELLER |
| `user` | `Demo12345!` | USER |

```bash
cd development
docker compose up -d mysql redis kafka shopstack-server shopstack-ui nginx
./seed/seed-demo.sh
```

The script is idempotent: it replaces the demo rows instead of duplicating them.
Product photos live in `images/` and are stored as blobs, which is how the
application models them.
