# Draft articles

Author submissions land here before they're published. To contribute, add a
folder named with your article's URL slug, containing an `index.md` (start from
`template/post.md`, renamed to `index.md`) and any images:

    draft/
      your-article-slug/
        index.md
        an-image.png

To write in AsciiDoc instead, name the file `index.adoc` and start from
`template/post.adoc`. The folder shape, the frontmatter and the checks are the
same either way.

Then open a pull request. **Leave `date:` as it is in the template** — don't
pick a folder or a date yourself. A maintainer reviews the submission, sets
`date:` and moves the folder into
`content/posts/<year>/<month>/<day>/your-article-slug/` to match, so the two
can never disagree. Only set a date yourself if you need the article out on a
particular day; see `template/post.md` for how.

Dating a post in the **future** schedules it: it is not built, listed or
searchable until that day, and it shows up under "Coming soon" on the home page
in the meantime. Write the day only, with no time — every post publishes at the
one daily build (07:00 UTC), and a time can only delay it. See `template/post.md`.

To preview a draft locally, run `hugo server` and either copy the folder into
`content/posts/<year>/<month>/<day>/` with today's date, or give it a future
`date:` and run `hugo server --buildFuture`. See `CONTRIBUTING.md`.

See **How To Submit Your Next Article On Foojay.io** (/today/how-to-submit-your-next-article-on-foojay-io/)
for the full guide, and `template/` for the starter files. This folder is outside
`content/`, so drafts are not published until a maintainer moves them.
