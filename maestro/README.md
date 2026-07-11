# Maestro UI flows

End-to-end UI tests for The Family App, one flow per page. Authored against the
debug build on a real device (Samsung SM-A175F) — they assume the app is already
**signed in** (auth/login/register are intentionally not covered).

## Running

Install Maestro, then from the repo root:

```bash
# one page
maestro test maestro/shopping.yaml

# everything
maestro test maestro/
```

Or via the Maestro MCP `run` tool with `dir: "maestro"`.

## Notes

- Each flow `launchApp` without clearing state (keeps the session) and navigates
  from Home, so flows are independent and can run in any order.
- Flows that create data (item / event / wish / birthday / meal) **delete it again**
  at the end, so they're safe to re-run repeatedly without leaving residue.
- A few selectors are positional (`point:`) where the underlying control exposes no
  stable text (e.g. swipe-to-delete trash, top-bar back). These are tuned for a
  ~1080×2340 device; adjust the percentages if your screen differs.
- `tags` on each flow let you filter, e.g. `maestro test maestro/ --include-tags=smoke`.
