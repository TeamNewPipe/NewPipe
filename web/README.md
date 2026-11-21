# WizeStream website

A responsive, multipage static product website and handbook. All website files live in `web/`; the Android app does not depend on them.

## What is included

- Homepage, feature overview, downloads, screenshots, comparison, FAQ, privacy, support, credits and release availability pages.
- Fifteen detailed guides covering installation, services, playback, SponsorBlock, subscriptions, downloads, profiles/sync, migration, learning, themes, devices, live/local media, privacy and troubleshooting.
- A comparison of 35 features across WizeStream, NewPipe, PipePipe, LibreTube and historical Tubular, with official references and explicit unknowns.
- Local full-text search, guide/FAQ filters, project/category/feature comparison filters, a mobile navigation menu and light/dark appearance.
- Real project screenshots and logo served locally. No analytics, remote fonts, third-party scripts, package manager or API key is needed. The static handbook needs no backend; optional community reviews and aggregate download counters use PHP.

The checked-in HTML and public `reviews/index.php` are the deployment output. The review backend and deployment instructions are in [reviews/README.md](reviews/README.md). Navigation, article text, the comparison table and FAQ work without JavaScript. JavaScript enhances search, filtering, navigation and the optional saved color preference. All links are relative, so the site supports a domain root or a project subdirectory.

## Build and check

Python 3.10+ is sufficient for the build and structural checker; there are no Python dependencies. Running the optional services additionally requires PHP, the review service's MySQL setup, and the private download-counter state described in `badges/README.md`.

```sh
python3 web/build.py
python3 web/check.py
```

Also check JavaScript syntax with Node.js when available:

```sh
node --check web/assets/site.js
node --check web/assets/search-index.js
```

For a local browser preview, run the standard-library server from the repository root:

```sh
python3 -m http.server 8000 --directory web
```

Open `http://localhost:8000/`. Check narrow phone layouts, desktop layouts, both themes, keyboard navigation, the search dialog and all filters. The structural checker is useful for regressions; it is not a substitute for browser, screen-reader or device testing.

## Edit the source, then rebuild

| File | Purpose |
| --- | --- |
| `build.py` | Shared templates, page copy and a small, deliberately limited Markdown renderer |
| `content/site.json` | Release/snapshot metadata, guide catalog and sourced comparison data |
| `content/docs/*.md` | Guide text; headings begin at `##` because the template supplies the page title |
| `assets/site.css` | Responsive layout, themes, reduced-motion and print styles |
| `assets/site.js` | Browser-only enhancement behavior |
| `assets/images/` | Locally served project graphics |
| `check.py` | Internal links/anchors, markup structure, metadata, search and comparison checks |

`build-manifest.json`, `assets/search-index.js`, `robots.txt` and all HTML pages are generated. Commit regenerated files with their source changes. Do not edit only a generated page.

The guide Markdown subset supports paragraphs, `##`–`####` headings, flat lists, tables, fenced code, inline code, bold text and links. It intentionally does not support arbitrary HTML or executable content. Guide links are relative to the resulting `docs/*.html` page. Use `.html` links, not `.md`, for other website guides.

## Keep factual and release context current

The initial content snapshot is reviewed on 13 November 2026, with stable 1.41.0 listed separately from the reviewed development branch. In particular, FreeTube transfer, SponsorBlock contributions/disclosures and independent off-by-default 360°/A–B switches are marked as newer development work. Do not remove those labels merely because a change is merged.

The links for the current stable release point to GitHub’s latest-release page, which lists both architecture-specific APKs. Direct website downloads use the architecture-specific `download.php` routes. Historical milestone cards link to their specific tags. Update the review date, pinned source commit, competitor versions and feature evidence together when doing a new factual review.

The comparison describes selected capabilities rather than declaring an overall winner. Preserve competitors' verified strengths. `unknown` means not established by the reviewed official sources, not a proven absence. The “Differences only” filter compares capability status among visible projects; feature notes can still differ when statuses match. Archived Tubular is explicitly historical. Refer to the linked tagged docs/source files before changing a cell.

## Deploy

Serve the **contents of `web/`** from any ordinary static host, including your own HTTPS web server. No build has to run on the server. There is no client-side routing fallback: direct requests for `docs/playback.html`, for example, should return that file.

The configured production address is **https://wizestream.org/**. `content/site.json` supplies that default for canonical/Open Graph URLs and the sitemap. Override it only when intentionally building for another deployment, including a project path when applicable:

```sh
python3 web/build.py --base-url https://wizestream.org/
python3 web/check.py
```

The default build uses the confirmed production address. To intentionally omit canonical URLs/sitemap for an offline preview, pass `--base-url ""`. A domain in configuration does not deploy the website or establish that the review backend is live.

For a static-only deployment artifact, include the generated `*.html` files, `docs/*.html`, `assets/`, `robots.txt` and `sitemap.xml` if generated. To enable reviews on GoDaddy, also upload the complete `reviews/` directory with its hidden `.htaccess` files, import the SQL schema, and place actual configuration outside every served document root. To enable direct downloads and badges, upload `badges/`, `download.php`, and `downloads/`, then follow `badges/README.md` and `downloads/README.md`. Follow `reviews/README.md` for reviews. The Python/Markdown/JSON source can stay in the repository; it is public and contains no production secrets. The real PHP config and database credentials must stay outside the website and repository. Hosting the full `web/` directory also works. This change does not activate any hosting service or publish a domain.

Recommended host configuration: HTTPS, correct HTML/CSS/JavaScript/SVG/PNG MIME types, short cache lifetimes or revalidation for HTML and the unversioned CSS/JS, and `X-Content-Type-Options: nosniff`. Avoid permanent immutable caching of assets with unchanged filenames. If your host supports headers, a compatible policy is:

```text
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'none'; object-src 'none'; base-uri 'self'; form-action 'none'; frame-ancestors 'none'
Referrer-Policy: no-referrer
```

The optional appearance preference uses `localStorage` key `wizestream-site-theme`. Only when reviewers opt in to "Remember my review," the reviews page stores a private management credential in browser `localStorage` under `wizestream-private-review-access-v1`; no account, tracking cookie or identifier is created. Anyone with access to that browser profile may manage saved reviews. My Reviews can forget the saved credential without deleting the public review, and browser site-data clearing also removes it. Search/filter input is not sent anywhere. No service worker or tracking cookie is installed. For `/reviews/`, exclude the static `connect-src 'none'` host policy above: PHP supplies a compatible `connect-src 'self'` policy for its same-origin requests. Allow `connect-src 'self'` on `downloads.html` so its themed counters can read `badges/summary.php`. Review responses use `no-store`. The website privacy page leaves hosting logs to the actual hosting configuration rather than promising a server policy that has not been configured.

## Graphics and attribution

`logo.svg` is copied from `assets/wizestream_logo_round.svg`, contributed to WizeStream by Fabián PS (@FabianOvrWrt). The four PNGs are copied from the English Fastlane phone screenshots (`08`, `05`, `06`, `07`). They are real repository assets, not newly invented screens. Their captions explicitly explain that theme, version and device can change the appearance. Preserve the repository's GPLv3 licensing and attribution; see the site About page and root license/component notices.

Show only download totals calculated by the included aggregate endpoints; keep the site free of invented totals, testimonials, ratings or live review claims. The PHP review implementation is included, with its actual deployment and credentials configured separately. Do not claim it is live or enable the app action before verifying it on the host.
