---
title: "Scraping websites and bot-protection"
date: "2026-10-15"
description: "The levels of bot protection I met while scraping genealogy sites, from a bare curl to shell-launched Chrome, and how to get past each one."
canonical: "https://blog.frankel.ch/bypass-bot-protection/"
authors:
  - "nicolas-frankel"
image: "cover_large.jpg"
categories:
  - "Security"
  - "Tools"
---

While searching for my ancestors, I had to access data from many different websites. I noticed there were different levels of protection against automated access depending on the site. In this post, I'll list them and mention countermeasures you can take to bypass them anyway.

> **Warning:** As a private person using the data for my own personal use, I have no qualms about bypassing the protection. It can be considered fair use. However, I don't know about your context, and I'm not saying you should. Scrape responsibly.

## The need for bot protection

Genealogy and public records sites provide lots of data. Many actors are interested in this data: it's normal, and even expected, that providers want to protect their assets.

The usual `robots.txt` approach is a simple request to bots.

> Despite the use of the terms allow and disallow, the protocol is purely advisory and relies on the compliance of the web robot; it cannot enforce any of what is stated in the file. Malicious web robots are unlikely to honor robots.txt; some may even use the robots.txt as a guide to find disallowed links and go straight to them.
>
> —- [Wikipedia](https://en.wikipedia.org/wiki/Robots.txt#Security)

Thus, most sites require actual anti-bot protection.

## No protection

The base level has little to no protection. Some sites do a surface check, but it's not serious protection. The check differs from site to site.

`curl` options are enough to access the data:

* User agent: pass the `User-Agent` via `-A`, _e.g._, `-A "Mozilla/5.0"`
* Referer: the referer is the URL of the page from which the link was clicked. Some sites check it on image requests only, and return a 403 without it. Use the `-e` option, _e.g._, `-e https://frankel.ch/`.
* Session cookies: this one is a bit more involved, but still easily manageable. `curl` provides the `-c` and `-b` options to store and load cookies.

    ```bash
    curl -c cookies.txt https://frankel.ch                # 1
    curl -b cookies.txt https://frankel.ch                # 2
    curl -b cookies.txt -c cookies.txt https://frankel.ch # 3
    ```

    1. Store cookies to `cookies.txt`
    2. Load cookies from `cookies.txt`
    3. Load and store, thus maintaining cookie-based session state

If a site at this level still refuses `curl`, it's probably fingerprinting the TLS handshake. Browsers use fixed combinations of ciphers and extensions, and the digest of the combination is a fingerprint, [JA3 or JA4](https://developers.cloudflare.com/bots/additional-configurations/ja3-ja4-fingerprint/), that identifies the client, whatever the `User-Agent` says. [`curl_cffi`](https://curl-cffi.readthedocs.io/en/latest/impersonate/fingerprint.html) can impersonate a browser's fingerprint from Python. I never needed it: by the time a site fingerprints TLS, it usually also requires JavaScript, which is the next level.

## Lack of JavaScript

At this step, the site requires JavaScript. It might be either a genuine attempt at protecting against bots, or just that the developer designed a site fully JavaScript-based. The second case is more frequent than you'd think: search forms built from JavaScript widgets, image viewers that populate their cache lazily, catalogues that load their tree on click. Nothing hostile, just no HTML to `curl`.

I encountered two different bot guards.

* [Anubis](https://anubis.techaro.lol/):

    > Anubis is an open source software program that adds a proof of work challenge to websites before users can access them in order to deter web scraping. It has been adopted mainly by Git forges and free and open-source software projects.
    >
    > Anubis was created by Xe Iaso in response to Amazon's web crawler overloading their Git server, as the crawler did not respect the robots.txt exclusion protocol and would work around restrictions.
    >
    > —- [Wikipedia](https://en.wikipedia.org/wiki/Anubis_(software))

    ![Anubis challenge screen](https://thumb.wikimedia.org/wikipedia/commons/thumb/5/50/Anubis_challenge_screen_%28dark%29.png/960px-Anubis_challenge_screen_%28dark%29.png)

    The challenge is a [Hashcash-style proof of work](https://lwn.net/Articles/1028558/): the browser appends a number to the challenge and hashes the result with SHA-256. It must find a number whose hash starts with a configurable count of zeros, in hexadecimal. A passing client gets a signed JWT cookie, valid for a while. A client without JavaScript gets nothing. For the record, Anubis' own documentation site is behind Anubis, and it blocked the fetcher I used to look up the documentation while writing this post.

* Cloudflare JavaScript detection:

    > JavaScript Detections is implemented on your website via a lightweight, invisible JavaScript code snippet that follows Cloudflare's privacy standards.
    >
    > JavaScript is injected only in response to requests for HTML pages or page views, excluding AJAX calls. API and mobile application traffic is unaffected.
    >
    > —- [Cloudflare docs](https://developers.cloudflare.com/cloudflare-challenges/challenge-types/javascript-detections/)

To bypass any of them, or similar bot-protection mechanisms, just use a browser. You can steer it with browser-automation software, such as [Playwright](https://playwright.dev/). On several sites, a headless browser was enough.

Not on all of them, though. Cloudflare's [own documentation](https://developers.cloudflare.com/bots/concepts/bot-score/) says JavaScript Detections "catches headless browsers (browsers controlled by software, with no visible window or human operator) and other automation tools". Whether a headless browser gets through a JavaScript check depends on how the site is configured.

## Headless browser

This is the level where the fun stuff begins. Some sites do reject headless browsers. I can only hint at some reasons, though I didn't dig deeper:

* Client fingerprinting: the TLS handshake and the HTTP/2 settings differ between a browser and a Python client, as seen above. They don't differ between headed and headless mode of the same binary. However, Playwright's default headless mode runs [`chrome-headless-shell`](https://developer.chrome.com/docs/chromium/headless), a different binary, unless you set a `channel`.
* Rendering environment fingerprinting: headless has no real GPU. Screen size, colour depth and device pixel ratio are canned. Fonts differ. Canvas and AudioContext fingerprints come out different.
* Declared identity: headless Chrome still sends a `User-Agent` containing `HeadlessChrome`. It also sets `navigator.webdriver` to `true`. That property is a [W3C standard](https://www.w3.org/TR/webdriver2/#interface), "a standard way for co-operating user agents to inform the document that it is controlled by WebDriver". Chromium [sets it](https://source.chromium.org/chromium/chromium/src/+/main:content/child/runtime_features.cc) for `--headless`, for `--enable-automation`, and for `--remote-debugging-pipe`, the channel Playwright uses to talk to the browser. Note that the last two apply whether the browser is headless or not. The `--disable-blink-features=AutomationControlled` argument hides the property, but it's no guarantee.
* Missing JavaScript objects: `window.chrome` and the plugin list used to be empty in headless Chrome. Chrome unified headless and headed modes in version 112. Since version 132, the old mode only survives as the standalone `chrome-headless-shell` binary. That's the binary Playwright's default headless mode runs on, so the old signals still apply there.
* Irrelevant GUI data: `outerWidth` equals `innerWidth` in headless, because there's no window frame around the page. In a real window, they differ.
* Behaviour: no mouse movement, no scroll, instant form fills, and page loads never request favicons or prefetches.

I experienced two components that implemented one or more of the above:

* [Turnstile](https://www.cloudflare.com/products/turnstile/):

    > Cloudflare Turnstile confirms web visitors are real and blocks unwanted bots without slowing down web experiences for real users. It's a simple snippet of free code that eliminates CAPTCHAs. With Turnstile, you can deliver better experiences and strengthen privacy for all users.
    >
    > —- [Cloudflare](https://www.cloudflare.com/products/turnstile/)

* [Imperva](https://www.imperva.com/products/advanced-bot-protection-management/).

The product doesn't decide the level; the site's configuration does. I met Turnstile on one site where a headed browser passed, and on another where it didn't.

To get past most of them, use a headed browser. You can steer it with Playwright, or any other browser automation API. Unfortunately, it means you need a system with a GUI, or somehow manage to forward the display to an X server. It works perfectly for me on my personal laptop.

Some sites needed two more things:

**A persistent browser profile**

When a human clears the challenge once in a profile, the browser gets a clearance cookie. Cloudflare [documents it](https://developers.cloudflare.com/cloudflare-challenges/concepts/clearance/):

> A `cf_clearance` cookie proves to Cloudflare that the visitor is a verified human and has passed Cloudflare's client-side verifications. The cookie is securely tied to the specific visitor and device it was issued to, preventing reuse across machines.
>
> —- [Cloudflare docs](https://developers.cloudflare.com/cloudflare-challenges/concepts/clearance/)

Copying the cookie into a fresh browser context doesn't work: the fingerprint of the browser presenting the cookie must match the one that earned it. I tested it on the same site the same day: a fresh context got an unsolvable challenge; the reused profile got no challenge at all.

**A real branded Chrome**

Not the Chromium that Playwright bundles! Playwright's [`launch_persistent_context`](https://playwright.dev/python/docs/api/class-browsertype#browser-type-launch-persistent-context) handles both:

```python
context = await pw.chromium.launch_persistent_context(
    user_data_dir=".browser-profiles/some-site",             # 1
    channel="chrome",                                        # 2
    headless=False,                                          # 3
    args=["--disable-blink-features=AutomationControlled"],  # 4
)
```

1. Persistent profile: cookies, local storage, and the clearance live here
2. Branded Google Chrome instead of the bundled Chromium
3. Headed
4. Hide `navigator.webdriver`

Clear the challenge by hand once, and never close the context at the end of a script: closing it closes the session.

## Browser automation

Even headed browsers can't defeat advanced protections. More precisely, headed browsers _launched by the automation tool_ can't. On one site, I used an automated headed browser when in front of the computer. The website showed an "I'm a human" CAPTCHA checkbox. I clicked it. Several times. To no avail.

Here's what I measured on that site, with the same stored profile in both cases:

| Chrome started by | HTTP status | Details |
| --- | --- | --- |
| Playwright, headed, `AutomationControlled` disabled, profile previously cleared by a human | 403 | Empty body, no challenge served at all |
| The shell, with `--remote-debugging-port`, then Playwright attached to it | 200 | Page content, seven cookies including the clearance |

Everything is the same, except who launched the browser. The site somehow sniffs the launch method, before even offering a challenge!

I don't know which signal it uses. Playwright launches Chrome with a list of switches of its own: `--enable-automation`, `--remote-debugging-pipe`, `--disable-extensions`, `--disable-field-trial-config`, a long `--disable-features=` list, and `--use-mock-keychain`. One known difference: Chromium sets `navigator.webdriver` for `--remote-debugging-pipe`, but for `--remote-debugging-port` only when the port is `0`. On port 9222, the property stays `false` without any extra flag.

The conclusion is pretty straightforward: launch Chrome yourself, and let the automation tool attach to it.

```bash
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome \
  --remote-debugging-port=9222 \
  --user-data-dir="$PWD/.browser-profiles/some-site" \
  --no-first-run --no-default-browser-check &
```

Solve the challenge once in that window, by hand. Don't navigate while the challenge runs. Then attach:

```python
browser = await pw.chromium.connect_over_cdp("http://127.0.0.1:9222")
context = browser.contexts[0]                                          # 1
page = await context.new_page()                                        # 2
await page.goto(url)
text = await page.evaluate("document.body.innerText")
await page.close()
```

1. The existing context, with the cleared profile
2. A new tab per fetch; close the tab, never the context

Playwright [documents](https://playwright.dev/python/docs/api/class-browsertype#browser-type-connect-over-cdp) `connect_over_cdp`, and warns that it "is significantly lower fidelity than the Playwright protocol connection". Some events don't fire. I read the page text and parse it myself instead of intercepting responses.

Two traps cost me time.

The first one is silent. Since Chrome 136, [the debugging port is ignored on the default profile](https://developer.chrome.com/blog/remote-debugging-port):

> These switches will no longer be respected if attempting to debug the default Chrome data directory. These switches must now be accompanied by the `--user-data-dir` switch to point to a non-standard directory.
>
> —- [Chrome for Developers](https://developer.chrome.com/blog/remote-debugging-port)

There's no warning on the console and no error in the window. Chrome starts normally, and the port just isn't there. The symptom is `curl http://127.0.0.1:9222/json/version` failing to connect while `ps` shows the flag on the command line.

The second one is just as silent. Launching Chrome while another instance uses the same profile hands the launch over to the running instance. The debugging port never opens. Quit Chrome fully first.

## Human

Not every automation-hostile site becomes reachable once a human drives the browser. One site detected my own, hand-driven, everyday Chrome. There was no CAPTCHA to solve and no clearance to earn. I gave up and found the data on a mirror.

Paywalls are the same story. A site that masks fields for free-tier accounts isn't detecting bots; no browser trick reveals them.

## Bonus: rate limiting

Rate limiting is orthogonal to all the levels above. On one site, after half a dozen rapid requests, everything stopped working at once: `curl`, Playwright, and my own hand-driven everyday browser got the same refusal. The site had blocked my IP, regardless of the client behind it.

I tried to connect through a VPN, which gave me another client IP, and the site worked again immediately. For later runs, I self-throttled with a `sleep` between requests and never hit the limit again.

Another site caps the number of search queries per day, regardless of the client. There's no trick there either: treat a capped query as not run rather than as a negative result.

## Summary

| Symptom | Level | Tool |
| --- | --- | --- |
| Works, or 403 without a `User-Agent`/`Referer` | No protection | `curl -A -e -b -c` |
| Empty page, challenge page, or "enable JavaScript" | Lack of JavaScript | Headless Playwright |
| Challenge that never resolves headless, content to a window | Headless browser | Headed Chrome, persistent profile, one manual clearance |
| 403 or an unsolvable CAPTCHA to a Playwright-launched window | Browser automation | Shell-launched Chrome with `--remote-debugging-port` and `--user-data-dir`, `connect_over_cdp` |
| Your own browser is blocked | Human | Hope you find another source |
| Everything fails at once, whatever the client, after a burst of requests | Rate limiting | Self-throttle |

<hr>

_Originally published at [A Java Geek](https://blog.frankel.ch/bypass-bot-protection/) on October 11<sup>th</sup>, 2026._
