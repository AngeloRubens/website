# `scripts/validate/` — PR-time and deploy-gate checks

Read the root `AGENTS.md` first; this file holds the detail for this folder.

- **`scripts/validate/Frontmatter.java`** — PR-time content check run by
  `pr-check.yml`, in lieu of a preview (GitHub Pages has no per-PR URLs).
  Required fields present; no dangling `related_posts`; no sponsor `authors:`
  slug without an author bundle; no emoji in a post title; no two pages in a
  folder claiming one series `weight`; a post's `date:` matching its folder;
  closed key sets on events, ads and sponsor author entries; no self-alias.

  **`checkDrafts` covers `draft/`, which nothing else can see.** Drafts live
  outside `content/` so they don't publish themselves — which also means the
  Hugo build never reads one, so a submission missing every required field and
  naming a nonexistent author went green. It applies the rules a published post
  gets, because publishing is only a maintainer moving the folder.

  **It is the ONE hard check scoped to `--changed-since`, deliberately.**
  Everything else guards `content/`, which publishes, so whose PR introduced a
  break is beside the point. `draft/` is a staging area where a submission
  legitimately sits half-finished — an article waiting on its author's profile,
  or the reverse. Checking every draft on every PR failed everybody else's
  unrelated work over someone's unfinished submission. The skip is printed, and
  an unscoped local run still checks all of them.

  **`//JAVA 21+`, not 17.** `TITLE_EMOJI` uses `\p{IsExtended_Pictographic}`,
  which only arrived in JDK 21 — on 17 it throws from the static initialiser,
  so the whole check dies before a single rule runs. It stayed hidden because
  this script runs only on a **pull request**, and most fixes go straight to
  `main`.

  This is why **`.github/PULL_REQUEST_TEMPLATE.md` is not a checklist.** It was
  one, and every item had rotted: it asked for `tags` (no such taxonomy), for
  `draft: false` (no such mechanism), and for a `hugo server -D` preview that
  cannot render a draft. The checks moved into the script; the template asks
  the one question a machine can't answer — what's in this PR.

- **`scripts/validate/BuiltSite.java`** — the check on what Hugo actually
  *produced*, run by `pr-check.yml` and again by `build-deploy.yml` **between
  the build and the deploy**, so a broken build stops before it replaces the
  live site. It reads `public/` off disk, so it needs no server, takes ~5
  seconds over half a million links, and cannot be flaky. External links are
  not checked — a third party being down is not a reason to block our deploy.

  Two checks, both derived, so there is no URL list to keep in step:
  1. **Every source page produced a built page**, through the permalinks in
     `hugo.toml`. This is what catches a whole *section* vanishing — the
     branch-bundle failure mode, where a `where` still parsed and matched
     nothing. A `bundles` flag per section keeps bundle resources out of it.
  2. **Every internal link resolves** — `href`, `src`, `srcset`, `poster` and
     the meta-refresh in every alias page. It subsumes a "the nav works" check,
     since a dead menu entry is a dead link on 4200 pages.

  **The two kinds of dead link are not the same problem, and only one may stop
  a deploy.** A link the TEMPLATES emit is broken for every reader and is a bug
  in this repo, so it fails the run. A link an author typed inside their own
  article is a fact about 2000 imported posts, so those are reported with a
  count — blocking every future deploy on a 2021 typo is how a gate gets
  switched off within the week. The boundary is **`.prose`**, which is exactly
  where `.Content` renders; `--strict` fails on those too, for a cleanup pass.

  Four things are load-bearing:
  - **The base path is read from the home page's own `canonical`**, not
    configured, so it is `/` in production and the subpath under any preview.
  - **A root-relative link not starting with the base path** is its own kind of
    failure ("escapes the base path") — it resolves on a laptop and 404s once
    deployed under a subpath, which is invisible locally.
  - **Existence is tested against a `Set` of the build's filenames**, not the
    filesystem, which makes it case-SENSITIVE everywhere. macOS is not and
    GitHub Pages is.
  - **Percent-escapes are decoded by hand**, not with `URLDecoder`, which turns
    `+` into a space. The emoji `aliases:` are why this matters.

  It has been seen to FAIL, which is the only way a green check means anything.
