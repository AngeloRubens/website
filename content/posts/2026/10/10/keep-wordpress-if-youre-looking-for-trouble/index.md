---
title: "Keep WordPress If You're Looking for Trouble... Or Read This!"
date: "2026-10-10"
description: "~13K WordPress sites get hacked every day. Here's why static site generators are the answer, and how to migrate."
authors:
  - "andy-damevin"
image: "cover.jpg"
categories:
  - "Security"
  - "Opinion"
related_posts:
  - "announcing-the-new-foojay"
  - "foojay-podcast-102"
---

> *I wrote this article by hand. AI was used to review grammar, style, and fact-check the data.*

WordPress made it easy for anyone to build a website. So easy that [43% of the web](https://w3techs.com/technologies/details/cm-wordpress) runs on it. Twenty years later, it's just as easy for anyone to break into one. If that sounds dramatic, keep reading. You can also [listen to our podcast](https://foojay.io/today/foojay-podcast-102/) on the topic.

11K plugin vulnerabilities in 2025/26 (+42% YoY), exploit time on critical vulnerabilities: 5 hours ([source](https://patchstack.com/whitepaper/state-of-wordpress-security-in-2026/)). The question is not if they can pwn your website, it is when.

**~13K WordPress websites get hacked every day. When will it be your turn?**

Here are a few things that could happen:

- Unwanted (hard to catch) redirections to nasty websites (drugs, sex, …)
- Being defaced: your page content replaced to promote something else
- Losing your data (sometimes ten years of content, see the [AlpesJUG post](https://iamroq.dev/posts/pwned-how-roq-saved-the-day/))

## What's wrong with my WordPress?

The core is actually pretty safe, the main issues come from plugins:

- if you don't update them, you're toast because vulnerabilities are constantly being discovered.
- if you update them, also screwed: supply chain attacks now use updates as the delivery mechanism.

On top of that, the WordPress ecosystem is going through a governance crisis: legal battles, mass departures from Automattic, and the first market share decline in 20 years. Not the best time to bet on it.

Let's also mention that WordPress requires a running server and database. That's great in theory: you get dynamic pages!

However:

- It renders pages on every request (caching helps, but you're patching the symptom)
- It is slow
- The plugins run in your production environment (💀)
- It is slow
- And it needs proper load testing... did I mention that famous Java conference whose WordPress site (with caching!) went down for the entire event? 😅
- It is slow
- The database can also be hacked and it contains a lot of sensitive data including your whole content

## Static is the way!

Simply because: with a static site generator, plugins only run at build time. They produce static files and disappear. No server-side code runs in production. Your content lives in git, where security and versioning are handled by providers like GitHub or your own. This is exactly how [Roq](https://iamroq.dev) works.

Did I mention that GitHub also lets you generate and publish your static site for free with [GitHub Pages](https://pages.github.com/)? But then again, if you knew that, why would you have picked WordPress in the first place? 🤨

**Tip:** GitHub Pages works with private repositories too, so your source stays private while your site is public. This requires a GitHub Team subscription ($4/user/month).

I hear you say, "but static is static and I need dynamic" (comments, users, payments, ...). For this you should either:

- When possible, fetch the data at build time and rebuild on a schedule. Dynamic content doesn't mean it needs to be generated on every request.
- Use external service providers (or build your own).

Here are a few examples:

- Comments: [Giscus](https://giscus.app/) (free, uses GitHub Discussions), [Disqus](https://disqus.com/)
- Forms: [Formspree](https://formspree.io/) (free tier)
- Payments: [Stripe Checkout](https://stripe.com/payments/checkout)
- Search: [Lunr](https://lunrjs.com/) (free, Roq has a plugin)
- Auth: [Auth0](https://auth0.com/) (free tier)
- Analytics: [Umami](https://umami.is/) (free, open-source, privacy-friendly)

Some features don't even need an external service: static site generators have plugins too, and they only run at build time. Roq has a growing [marketplace](https://iamroq.dev/marketplace/) with plugins for search, sitemap, tagging, diagrams, and more.

**Tip:** [Quarkus](https://quarkus.io/) provides an awesome way to create [full-stack web components](https://iamroq.dev/posts/add-comments-web-component/) that integrate directly into your pages. Perfect for building your own services. And with Quarkus on serverless, they scale to zero, meaning they're practically free when not in use.

*By the way, if this article were written with Roq instead of Hugo, these tips would render as proper admonition blocks out of the box 😉*

## What should I do now?

First, make sure your data and design are backed up somewhere safe… It would be a shame to get hacked just before migrating 😅!

Then, pick a static site generator. You can go with one you already know (Hugo, Jekyll, …) but as Roq's author, I might be a tiny bit biased 😊.

Roq is a Quarkus-powered (Java) static site generator that makes it easy and fun to build websites and blogs. It comes with a Notion-like block editor for writers, supports Markdown and AsciiDoc, and integrates well with your favorite AI tooling. Fast to start, simple to maintain.

## From WordPress to Roq

We prepared a step-by-step guide for this: [Migrate a WordPress blog to Roq](https://iamroq.dev/posts/migrate-from-wordpress-to-roq/).

If you want to keep your current look, you can ask AI to recreate your theme in Roq. Otherwise, take the opportunity to start fresh with a new design. At least the switch will be visible!

## Ready to switch?

- **Start fresh**: [Create a new Roq site](https://iamroq.dev/create/) in a few clicks, pick a theme, and deploy
- **Migrate from WordPress**: Follow our [step-by-step migration guide](https://iamroq.dev/posts/migrate-from-wordpress-to-roq/)
- **Learn more**: Visit [iamroq.dev](https://iamroq.dev) or listen to the [Foojay podcast episode](https://foojay.io/today/foojay-podcast-102/) where we discuss all of this
