# `tests/e2e/` — browser deploy gate

Read the root `AGENTS.md` first; `README.md` here is the full guide.

- **`tests/e2e/` — the browser half of the deploy gate**, run by
  `build-deploy.yml` after `BuiltSite.java`. These are the only checks that can
  see the parts of foojay that **exist only once JavaScript runs** — search,
  both maps, the lightbox, the sortable tables, syntax highlighting, mermaid,
  the calendar grid. Every one fails *silently*: the page still returns 200 and
  simply stops doing the thing. `tests/e2e/README.md` is the full guide.

  - **The "staging environment" is localhost.** `server.mjs` serves `public/`
    on 127.0.0.1 and is hand-written rather than `npx serve` because the point
    is to behave like **GitHub Pages specifically**: pretty URLs, a 301 on a
    missing trailing slash, `404.html` with a 404 status, `Range` support, and
    everything under the base path.
  - **Which pages get tested is DERIVED from the build.** `discover.mjs` finds
    the first post with a code block, a gallery, an embed, and so on. A
    hardcoded pick rots into a test that still *passes*, because the page still
    loads and simply has no diagram on it. A feature the build contains none of
    resolves to `null` and its test skips with a reason.
  - **Third parties are stubbed, never fetched**, so the suite is fast and
    cannot fail on someone else's bad afternoon. That is also why **there are
    no retries**: nothing depends on the network, so a failure that comes and
    goes is a real bug and a retry would hide it.
  - **"Does the video play" is two questions and only one belongs in a gate.**
    A third-party embed is checked structurally; the one self-hosted `.mp4` is
    fetched, range-requested and waited on until `canplay`. Existence is not
    playability — a killed encode once left a 0-byte file that every "does it
    exist" check called fine.
  - **Console errors are not collected** (a stubbed third party logs noise that
    says nothing about this site); `pageerror` and same-origin 4xx/5xx are.
  - **Nothing is resolved against the working directory, and
    `reuseExistingServer` is `false` even locally.** Playwright runs
    `webServer.command` with the config's own directory as cwd, so a relative
    path resolved to `tests/e2e/tests/e2e/`. It failed only in CI, because the
    default `!process.env.CI` had silently reused a stale local server on every
    local run — so the CI launch path had never been exercised once. **Don't
    put that default back.**
  - **`playwright.config.mjs`'s module body runs in EVERY worker**, so nothing
    that writes a file may live there — measured at 39 evaluations on one
    CI-shaped run, i.e. 39 truncating writes racing a read, and the loser died
    on `SyntaxError: Unexpected end of JSON input`. It is `global-setup.mjs`
    now, and the write is staged to a temp file and renamed.
  - **The gate must not count itself.** A browser walking 20 pages per deploy
    would add 20 reads to the numbers *printed on the site*. Two layers: the
    routes are stubbed, and Chromium runs with
    `--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1`, below the level
    any page script can reach — because the first is a Playwright-level promise
    and `sendBeacon` is exactly the request shape not to be wrong about.
  - **Pagefind's matching is fuzzy enough that nonsense still matches** —
    `xqjvbzkwqpfmdlrn` returns 214 results — so only several nonsense tokens
    ANDed together are a genuinely empty query. And **a `loading="lazy"` image
    below the fold has no box**, so it never becomes clickable until the test
    scrolls and waits for the decode.

  `@playwright/test` is pinned **at 1.56.1 or later**: every version below
  1.55.1 downloads browser binaries without verifying the TLS certificate.
