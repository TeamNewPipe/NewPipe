#!/usr/bin/env python3
"""Build the WizeStream static website with Python's standard library."""
from __future__ import annotations

import argparse
import html
import json
import posixpath
import re
import unicodedata
from datetime import date
from pathlib import Path
from urllib.parse import urljoin, urlsplit

ROOT = Path(__file__).resolve().parent
DATA = json.loads((ROOT / 'content/site.json').read_text())
REPO = DATA['repository']
LATEST_RELEASE = f'{REPO}/releases/latest'
SOURCE = f"{REPO}/blob/{DATA['sourceCommit']}/"
RELEASE_URL = f"{REPO}/releases/tag/v{DATA['release']}"
REVIEWED_DATE = date.fromisoformat(DATA['reviewed']).strftime('%d %B %Y').lstrip('0')
RELEASE_DATE = date.fromisoformat(DATA['releaseDate']).strftime('%d %B %Y').lstrip('0')
COMPARISON_REVIEWED_DATE = date.fromisoformat(DATA['comparisonReviewed']).strftime('%d %B %Y').lstrip('0')
PAGES = []
SEARCH = []
BASE_URL = ''

ICONS = {
    'play': '<path d="m9 5 11 7-11 7V5Z"/>',
    'arrow': '<path d="M5 12h14m-6-6 6 6-6 6"/>',
    'external': '<path d="M14 4h6v6m0-6L10 14M10 4H4v16h16v-6"/>',
    'search': '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
    'shield': '<path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z"/><path d="m8 12 3 3 5-6"/>',
    'library': '<path d="M4 4v16m5-16v16m5-16v16m3-15 4 14"/>',
    'download': '<path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/>',
    'compass': '<circle cx="12" cy="12" r="9"/><path d="m16 8-2 6-6 2 2-6 6-2Z"/>',
    'skip': '<path d="m4 5 10 7-10 7V5Zm13 0v14"/>',
    'cast': '<path d="M3 5h18v14h-7M3 13a6 6 0 0 1 6 6M3 9a10 10 0 0 1 10 10M3 18v1h1"/>',
    'radio': '<rect x="3" y="8" width="18" height="12" rx="2"/><path d="m5 8 13-5M7 12h7m-7 4h4"/><circle cx="17" cy="15" r="2"/>',
    'sync': '<path d="M4 10a8 8 0 0 1 14-5l2 2m0-5v5h-5M20 14a8 8 0 0 1-14 5l-2-2m0 5v-5h5"/>',
    'archive': '<path d="M4 7h16v14H4V7Zm-1-4h18v4H3V3Zm6 8h6"/>',
    'book': '<path d="M12 6C8 3 4 3 2 4v15c3-1 7 0 10 2 3-2 7-3 10-2V4c-2-1-6-1-10 2Zm0 0v15"/>',
    'sliders': '<path d="M5 3v6m0 4v8M12 3v11m0 4v3M19 3v3m0 4v11M2 9h6m1 9h6m1-12h6"/>',
    'palette': '<path d="M12 3a9 9 0 1 0 0 18h2a2 2 0 0 0 1-4c-2-1 1-3 3-3h1a3 3 0 0 0 3-3c0-5-5-8-10-8Z"/><path d="M7 8h.01M12 6h.01M17 8h.01M6 13h.01"/>',
    'help': '<circle cx="12" cy="12" r="9"/><path d="M9 9a3 3 0 0 1 6 0c0 2-3 2-3 5m0 3h.01"/>',
    'sun': '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1 1m12 12 1 1M5 19l1-1M18 6l1-1"/>',
    'menu': '<path d="M4 6h16M4 12h16M4 18h16"/>',
    'check': '<path d="m5 12 4 4L19 6"/>',
}


def esc(value):
    return html.escape(str(value), quote=True)


def icon(name, cls=''):
    return f'<svg class="icon {cls}" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">{ICONS.get(name, ICONS["play"])}</svg>'


def rel(path, target):
    if target.startswith(('https://', 'http://', '#', 'mailto:')):
        return target
    target_path, marker, fragment = target.partition('#')
    result = posixpath.relpath(target_path, posixpath.dirname(path) or '.')
    if target_path.endswith('/') and not result.endswith('/'):
        result += '/'
    return result + (marker + fragment if marker else '')


def link(path, target, label, cls=''):
    external = target.startswith(('https://', 'http://'))
    return f'<a href="{esc(rel(path, target))}"' + (f' class="{esc(cls)}"' if cls else '') + (' rel="noreferrer"' if external else '') + f'>{label}</a>'


def slug(text):
    value = unicodedata.normalize('NFKD', text).encode('ascii', 'ignore').decode().lower()
    return re.sub(r'[^a-z0-9]+', '-', value).strip('-')


def inline(text):
    parts = re.split(r'(`[^`]+`|\[[^\]]+\]\([^\s)]+\)|\*\*[^*]+\*\*)', text)
    result = []
    for part in parts:
        if part.startswith('`') and part.endswith('`'):
            result.append('<code>' + esc(part[1:-1]) + '</code>')
        elif part.startswith('**') and part.endswith('**'):
            result.append('<strong>' + esc(part[2:-2]) + '</strong>')
        elif match := re.fullmatch(r'\[([^\]]+)\]\(([^\s)]+)\)', part):
            label, url = match.groups()
            if urlsplit(url).scheme not in ('', 'https', 'http'):
                raise ValueError('Unsupported documentation link scheme')
            result.append(f'<a href="{esc(url)}"' + (' rel="noreferrer"' if url.startswith('https://') else '') + f'>{esc(label)}</a>')
        else:
            result.append(esc(part))
    return ''.join(result)


def markdown(source):
    """Render only the small Markdown subset used by these authored guides."""
    lines = source.splitlines()
    output, headings = [], []
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if not line:
            i += 1
            continue
        if line.startswith('```'):
            code = []
            i += 1
            while i < len(lines) and not lines[i].strip().startswith('```'):
                code.append(lines[i]); i += 1
            output.append('<pre><code>' + esc('\n'.join(code)) + '</code></pre>')
            i += 1
            continue
        if match := re.match(r'^(#{2,4}) (.+)$', line):
            level, title = len(match[1]), match[2]
            anchor = slug(title)
            output.append(f'<h{level} id="{anchor}">{inline(title)}</h{level}>')
            if level == 2:
                headings.append((anchor, title))
            i += 1
            continue
        if line.startswith('|'):
            table = []
            while i < len(lines) and lines[i].strip().startswith('|'):
                cells = [x.strip() for x in lines[i].strip().strip('|').split('|')]
                if not all(re.fullmatch(r':?-+:?', x) for x in cells):
                    table.append(cells)
                i += 1
            label = headings[-1][1] if headings else 'Documentation table'
            head = ''.join('<th scope="col">' + inline(x) + '</th>' for x in table[0])
            rows = ''.join('<tr>' + ''.join('<td>' + inline(x) + '</td>' for x in row) + '</tr>' for row in table[1:])
            output.append(f'<div class="table-scroll" role="region" aria-label="{esc(label)}" tabindex="0"><table><thead><tr>{head}</tr></thead><tbody>{rows}</tbody></table></div>')
            continue
        list_match = re.match(r'^(- |\d+\. )(.+)$', line)
        if list_match:
            ordered = list_match[1][0].isdigit()
            items = []
            while i < len(lines):
                item = re.match(r'^(- |\d+\. )(.+)$', lines[i].strip())
                if not item or item[1][0].isdigit() != ordered:
                    break
                items.append('<li>' + inline(item[2]) + '</li>'); i += 1
            tag = 'ol' if ordered else 'ul'
            output.append(f'<{tag}>' + ''.join(items) + f'</{tag}>')
            continue
        paragraph = []
        while i < len(lines) and lines[i].strip():
            if paragraph and re.match(r'^(#{2,4} |\||- |\d+\. |```)', lines[i].strip()):
                break
            paragraph.append(lines[i].strip()); i += 1
        output.append('<p>' + inline(' '.join(paragraph)) + '</p>')
    return '\n'.join(output), headings


def notice(path):
    source_note = (
        f'The source reviewed {REVIEWED_DATE} is the commit tagged for this release.'
        if DATA['sourceCommit'] == DATA['releaseCommit']
        else f'Guides also cover merged source reviewed {REVIEWED_DATE}; changes marked after {DATA["release"]} need a build containing them.'
    )
    return f'<aside class="availability"><span class="notice-icon">{icon("help")}</span><p><strong>Reviewed release: {link(path, RELEASE_URL, "WizeStream " + DATA["release"])} ({RELEASE_DATE}).</strong> {source_note} {link(path, "releases.html", "Check release availability ↗")}</p></aside>'


def hero(eyebrow, title, description, extra=''):
    return f'<header class="page-hero"><p class="eyebrow">{eyebrow}</p><h1>{title}</h1><p class="page-lead">{description}</p>{extra}</header>'


def cta(path):
    return f'<section class="download-cta"><div><p class="eyebrow">Make room for what you enjoy</p><h2>Your next stream starts here.</h2><p>Free and open-source software for Android. Choose the build that fits your device.</p></div>{link(path, "downloads.html", "Get WizeStream " + icon("arrow"), "button button-white")}</section>'


def doc_card(path, doc):
    return link(path, f'docs/{doc["slug"]}.html', f'<span class="card-icon">{icon(doc["icon"])}</span><h3>{esc(doc["title"])}</h3><p>{esc(doc["description"])}</p><span class="card-arrow">{icon("arrow")}</span>', 'guide-card')


def feature_card(path, symbol, title, description, target):
    return link(path, target, f'<span class="card-icon">{icon(symbol)}</span><h3>{title}</h3><p>{description}</p><span class="text-link">Explore {icon("arrow")}</span>', 'feature-card')


def photo(path, name, alt, cls='', eager=False):
    return f'<img src="{esc(rel(path, "assets/images/" + name + ".png"))}" width="575" height="1280" alt="{esc(alt)}" class="{esc(cls)}" loading="{"eager" if eager else "lazy"}" decoding="async"' + (' fetchpriority="high"' if eager else '') + '>'


def emit(path, title, description, body, section='', doc=False, search_body=None, php=False, scripts=()):
    active = section or path
    navigation = [('features.html', 'Features'), ('docs/index.html', 'Documentation'), ('compare.html', 'Compare'), ('reviews/', 'Reviews'), ('downloads.html', 'Download')]
    nav = ''.join(link(path, target, label, 'nav-link' + (' is-active' if target == active else '')) for target, label in navigation)
    foot_columns = [
        ('Explore', [('features.html','Features'),('compare.html','Comparison'),('screenshots.html','Screenshots'),('downloads.html','Downloads'),('reviews/','Community reviews')]),
        ('Learn', [('docs/index.html','Documentation'),('docs/getting-started.html','Getting started'),('faq.html','FAQ'),('releases.html','Release notes')]),
        ('Project', [('about.html','About & credits'),('support.html','Support & contribute'),('privacy.html','Privacy'),(REPO,'Source code ↗')]),
    ]
    footer_links = ''.join('<div><h2>' + heading + '</h2>' + ''.join(link(path, url, label) for url, label in links) + '</div>' for heading, links in foot_columns)
    canonical = ''
    if BASE_URL:
        absolute = urljoin(BASE_URL, path.removesuffix('index.php') if php else path)
        canonical = f'<link rel="canonical" href="{esc(absolute)}"><meta property="og:url" content="{esc(absolute)}"><meta property="og:image" content="{esc(urljoin(BASE_URL,"assets/images/logo.svg"))}">'
    logo = f'<img src="{esc(rel(path,"assets/images/logo.svg"))}" width="34" height="34" alt="">'
    root_prefix = posixpath.relpath('.', posixpath.dirname(path) or '.')
    document = f'''<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
  <title>{esc(title)} · WizeStream</title><meta name="description" content="{esc(description)}">
  <meta name="theme-color" content="#c32025"><meta name="referrer" content="no-referrer">
  <meta property="og:title" content="{esc(title)} · WizeStream"><meta property="og:description" content="{esc(description)}"><meta property="og:type" content="{"article" if doc else "website"}">
  <meta name="twitter:card" content="summary">{canonical}
  <link rel="icon" href="{esc(rel(path,'assets/images/logo.svg'))}" type="image/svg+xml">
  <link rel="stylesheet" href="{esc(rel(path,'assets/site.css'))}">{('<link rel="stylesheet" href="'+esc(rel(path,'reviews/reviews.css'))+'">') if php else ''}
  <script src="{esc(rel(path,'assets/search-index.js'))}" defer></script><script src="{esc(rel(path,'assets/site.js'))}" defer></script>
{''.join('<script src="'+esc(rel(path, script))+'" defer></script>' for script in scripts)}
</head>
<body data-base="{esc(root_prefix)}" data-page="{esc(path)}">
  <a class="skip-link" href="#main">Skip to content</a>
  <header class="site-header"><div class="header-inner">{link(path,'index.html',logo+'<span>WizeStream</span>','brand')}
    <button class="menu-toggle icon-button" type="button" aria-label="Open navigation" aria-controls="primary-navigation" aria-expanded="false" hidden>{icon('menu')}</button>
    <nav id="primary-navigation" class="primary-navigation" aria-label="Main navigation">{nav}</nav>
    <div class="header-actions">{link(path,'docs/index.html#search',icon('search')+'<span class="search-label">Search</span><kbd>⌘ K</kbd>','search-button')}<button class="theme-toggle icon-button" aria-label="Switch color theme" type="button" hidden>{icon('sun')}</button>{link(path,REPO,'GitHub '+icon('external'),'github-link')}</div>
  </div></header>
  <main id="main">{body}</main>
  <footer class="site-footer"><div class="footer-main"><div class="footer-brand">{link(path,'index.html',logo+'<span>WizeStream</span>','brand')}<p>Independent Android streaming.<br>A local library. More ways to play.</p><p class="small">Free and open source · GPLv3</p></div>{footer_links}</div><div class="footer-bottom"><p>WizeStream is an independent NewPipe-based project. Not affiliated with or endorsed by TeamNewPipe or NewPipe e.V.</p><span>Documentation reviewed {REVIEWED_DATE}</span></div></footer>
  <dialog id="search-dialog" class="search-dialog" aria-labelledby="search-title"><div class="search-dialog-heading"><h2 id="search-title">Search the documentation</h2><button type="button" data-close-search aria-label="Close search">Esc</button></div><label class="sr-only" for="site-search-input">Search guides and pages</label><input id="site-search-input" type="search" autocomplete="off" placeholder="Try “download window” or “Device Sync”"><p class="search-privacy">Search runs in your browser. Your query is not sent to a server.</p><div id="site-search-results" class="search-results"></div><p id="site-search-status" class="sr-only" aria-live="polite"></p></dialog>
</body></html>
'''
    if php:
        prefix = '''<?php
// Generated by web/build.py. Edit content/reviews.fragment and rebuild.
declare(strict_types=1);
define('WIZE_REVIEWS_ENTRY', true);
require __DIR__ . '/_private/bootstrap.php';
try {
    wize_require_https();
    $service = wize_service();
    foreach ($service->headers('text/html; charset=utf-8') as $name => $value) { header($name . ': ' . $value); }
    if (in_array($_GET['view'] ?? '', ['manage', 'moderate', 'mine'], true)) { header('X-Robots-Tag: noindex, nofollow'); }
} catch (Throwable) {
    http_response_code(503);
    header('Content-Type: text/html; charset=utf-8');
    header('Cache-Control: no-store');
    header('Referrer-Policy: no-referrer');
    echo '<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Community reviews · WizeStream</title><link rel="stylesheet" href="../assets/site.css"><main class="container page-hero"><h1>Reviews are not available yet.</h1><p>Please try again later.</p><a href="../index.html">Back to WizeStream</a></main></html>';
    exit;
}
?>
'''
        document = prefix + document
    destination = ROOT / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(document, encoding='utf8')
    PAGES.append(path)
    text = re.sub(r'<[^>]+>', ' ', body if search_body is None else search_body)
    text = re.sub(r'\s+', ' ', html.unescape(text)).strip()
    SEARCH.append({'title':title,'url':path,'description':description,'text':text[:30_000]})


def home():
    p = 'index.html'
    body = f'''<div class="home-hero-wrap"><section class="home-hero container"><div class="hero-copy"><p class="eyebrow"><span class="status-dot"></span> Independent. Open source. Android.</p><h1>Watch. Listen.<br><span>Make it yours.</span></h1><p class="hero-lead">Your favorite streams, a library you control, and playback that fits your day. All without a platform account.</p><div class="button-row">{link(p,'downloads.html',icon('download')+'Download WizeStream','button button-primary')}{link(p,'docs/getting-started.html','Start exploring '+icon('arrow'),'button button-secondary')}</div><ul class="hero-trust"><li>{icon('check')}Android 6.0+</li><li>{icon('check')}No account required</li><li>{icon('check')}GPLv3</li></ul><p class="release-caption">{link(p,LATEST_RELEASE,'Latest stable release')} · {link(p,'privacy.html','Privacy by design, with clear boundaries')}</p></div><figure class="hero-devices"><div class="hero-orbit" aria-hidden="true"></div><div class="phone phone-back">{photo(p,'channel','WizeStream channel page with local subscription and playback actions')}</div><div class="phone phone-front">{photo(p,'playback','WizeStream video details with background, popup and download actions',eager=True)}</div><div class="device-label">{icon('play')}Video. Audio. Your library.</div><figcaption class="sr-only">Project screenshots. Appearance varies with theme and app version.</figcaption></figure></section></div>
    <section class="service-strip container" aria-label="Supported sources"><p>More than one way to discover.</p><div><span>YouTube</span><span>YouTube Music</span><span>SoundCloud</span><span>PeerTube</span><span>Bandcamp</span>{link(p,'docs/services-discovery.html','And more '+icon('arrow'))}</div></section>
    <section class="section container"><div class="section-heading"><div><p class="eyebrow">Built around how you watch</p><h2>One app.<br>Several ways to play.</h2></div><p>Switch from watching to listening, keep a floating player nearby, or take supported media offline. Choose what works for you.</p></div><div class="feature-grid">{feature_card(p,'play','Playback that adapts','Main player, Listen mode, background audio, popup and optional native picture-in-picture.','docs/playback.html')}{feature_card(p,'library','A library that stays yours','Local subscriptions, playlists and profiles. Organize without rebuilding a platform account.','docs/subscriptions.html')}{feature_card(p,'download','Offline, on your schedule','Individual and batch downloads, optional channel downloads and daily download windows.','docs/downloads.html')}{feature_card(p,'sliders','Make room for your preferences','Material 3 surfaces, configurable navigation, accessible text and data-only theme packs.','docs/customization.html')}</div></section>
    <section class="spotlight container"><div class="spotlight-visual"><div class="phone compact-phone">{photo(p,'history','WizeStream history with watch progress and content filters')}</div><div class="visual-tag">{icon('library')}Locally organized. Personally useful.</div></div><div class="spotlight-copy"><p class="eyebrow">Follow what matters</p><h2>Your subscriptions.<br>Your own rhythm.</h2><p>Bring channels together in groups, tune What’s New, and separate a study library from everyday watching with local profiles.</p><ul class="check-list"><li>{icon('check')}Channel groups and filtered subscription feeds</li><li>{icon('check')}Local playlists, bookmarks and smart rules</li><li>{icon('check')}Optional encrypted sync between paired devices</li></ul>{link(p,'docs/profiles-sync.html','See how local profiles and sync work '+icon('arrow'),'text-link')}</div></section>
    <section class="section container"><div class="section-heading"><div><p class="eyebrow">Beyond the next video</p><h2>More of your media,<br>in one place.</h2></div><p>Read a transcript, revisit a timestamped note, browse a radio station or play a local file. Useful tools, with their limits explained.</p></div><div class="media-grid">{feature_card(p,'book','Learn at your pace','Searchable transcripts, timestamped notes and optional study tools for designated content.','docs/learning.html')}{feature_card(p,'radio','Discover live audio and TV','Radio Browser, community TV directories and your supported HTTPS/M3U sources.','docs/live-media.html')}{feature_card(p,'cast','Reach your other screens','Android TV controls, audio on Android Auto, and compatible FCast/Chromecast receivers.','docs/devices.html')}</div></section>
    <section class="privacy-band"><div class="container privacy-band-inner"><span class="large-icon">{icon('shield')}</span><div><p class="eyebrow">Privacy, explained clearly</p><h2>A local-first app.<br>Deliberate connections.</h2><p>No WizeStream account, advertising SDK or analytics SDK. Optional network features remain choices, and media services still receive normal connection information.</p>{link(p,'privacy.html','Understand the privacy model '+icon('arrow'),'text-link')}</div><div class="privacy-points"><span>{icon('check')}No platform sign-in required</span><span>{icon('check')}Manual error-report sharing</span><span>{icon('check')}Controls for history and optional services</span><span>{icon('check')}Readable, open-source code</span></div></div></section>
    <section class="section container"><div class="section-heading"><div><p class="eyebrow">Find the right fit</p><h2>Compare approaches,<br>not just checkmarks.</h2></div><p>See WizeStream alongside NewPipe, PipePipe and LibreTube. The comparison includes their strengths, release context and source references.</p></div><div class="comparison-teaser"><div><span class="teaser-number">05</span><h3>Projects, with context.</h3><p>Four active projects and a clearly marked historical Tubular reference. “Not verified” never means “definitely absent.”</p>{link(p,'compare.html','Open the full comparison '+icon('arrow'),'text-link')}</div><div class="teaser-facts"><div><span>Core playback</span><strong>Several capable choices</strong></div><div><span>Accounts & synchronization</span><strong>Different privacy and storage models</strong></div><div><span>Advanced controls</span><strong>Check the exact app and release</strong></div><div><span>Evidence</span><strong>Official documentation and source links</strong></div></div></div></section>
    <section class="section container section-top-border"><div class="section-heading"><div><p class="eyebrow">The handbook</p><h2>Get comfortable.<br>Then go deeper.</h2></div>{link(p,'docs/index.html','All documentation '+icon('arrow'),'text-link')}</div><div class="guide-grid">{''.join(doc_card(p,d) for d in DATA['docs'] if d['slug'] in ['getting-started','playback','downloads','backups-migration','privacy-network','troubleshooting'])}</div></section><div class="container">{cta(p)}</div>'''
    emit(p,'Watch, listen and make it yours','Independent Android streaming with local subscriptions, flexible playback, downloads, profiles and optional device sync.',body)


def features():
    p='features.html'
    sections=[
        ('compass','Streaming & discovery','Browse supported public sources without a platform login.',['YouTube with Music and Shorts destinations, plus SoundCloud, PeerTube, Bandcamp and media.ccc.de.','Integrated Bilibili, Niconico, BitChute, Rumble and Vimeo; experimental Odysee support.','Service-specific search, channel tabs and contextual search; optional public-only Vimeo API search/comments.','Optional local recommendations, saved searches and local content filters; signed external service plugins on Android 7+.'],'services-discovery'),
        ('play','Playback & audio','Keep useful controls close to the media.',['Main video, background, Listen, popup and optional native Android PiP.','Captions, searchable transcripts, chapters, audio-track selection and per-channel preferences.','Pinch zoom, fullscreen gestures, touch lock, session caption timing and non-live A–B repeat.','360° projection controls, a 10-band equalizer, visualizers and optional normalization/compression.'],'playback'),
        ('library','Local library','Build an organized collection on your device.',['Subscriptions, channel groups, What’s New filters and selected-channel notifications.','Local playlists, remote bookmarks, categories, separators and supported bulk editing.','Rule-based smart playlists over locally indexed media.','Separate supported libraries by profile; pause supported activity recording with Incognito.'],'subscriptions'),
        ('download','Downloads & offline','Choose the files and schedule that fit your device.',['Video, audio and caption downloads, plus supported full-video companion caption files.','Playlist/queue batches, optional automatic channel downloads and resumable transfer controls.','Daily download windows with optional charging and unmetered-network constraints.','Optional retention for tracked automatic downloads, previews and permanent-file protection.'],'downloads'),
        ('sync','Profiles, backups & sync','Move supported records intentionally.',['Encrypted peer-to-peer Device Sync on a reachable local network; QR scanning or manual code pairing.','Supported profile identities, subscriptions, playlists, history, progress and selected settings.','Manual backups, optional scheduled local backups, and compatible migration formats.','Download metadata can sync; actual video/audio files do not.'],'profiles-sync'),
        ('book','Learning & notes','Keep lessons distinct from casual watching.',['Timestamped notes and transcript search for supported content.','Optional Learning Mode for explicitly designated videos and playlists.','Learning dashboard, supported progress controls, study activity and review shortcuts.','Optional per-profile difficulty ratings, with local or supported synchronized storage.'],'learning'),
        ('radio','Live and local media','Use sources beyond an on-demand platform feed.',['Worldwide Radio Browser discovery with station filters, favorites and supported custom streams.','Live TV country directories from IPTV-org or Free-TV, plus M3U/HTTPS imports.','Per-profile TV playlists/favorites, cached lists and available alternative streams.','Local audio/video browser with folders, search, filters, exclusions and rescans.'],'live-media'),
        ('cast','Devices & receivers','Use the interface that matches the situation.',['Phone, tablet, landscape and Android TV layouts with supported D-pad shortcuts.','Audio browsing, search and resume through Android Auto.','FCast and Chromecast-compatible casting for supported receivers and stream formats.','Optional HTTPS yt-fifo/Snapcast home-audio handoff.'],'devices'),
        ('palette','Appearance & accessibility','Personalize without executable theme plugins.',['Material 3-oriented surfaces, dynamic or manual colors, and light/dark/black/system choices.','Configurable home navigation, text sizing, list/grid layouts and tablet columns.','Previewable JSON theme packs with supported palette, glass and native listening layouts.','Native controls and accessibility semantics remain important when using a custom skin.'],'customization'),
    ]
    cards=''
    for symbol,title,lead,items,target in sections:
        cards+=f'<section class="feature-detail" id="{slug(title)}"><div class="feature-detail-title"><span class="card-icon">{icon(symbol)}</span><h2>{title}</h2><p>{lead}</p>{link(p,"docs/"+target+".html","Read the guide "+icon("arrow"),"text-link")}</div><ul class="feature-bullets">'+''.join('<li>'+icon('check')+esc(item)+'</li>' for item in items)+'</ul></section>'
    body=f'<div class="container">{hero("Capabilities, with context","A complete streaming toolkit.","Flexible playback is the beginning. Explore the local library, offline tools, learning features and optional integrations around it.")}{notice(p)}<div class="feature-jump-links">'+''.join(link(p,'#'+slug(title),title) for _,title,*_ in sections)+f'</div>{cards}<section class="next-release"><p class="eyebrow">Included in {DATA["release"]}</p><h2>More ways to use your library.</h2><div class="three-columns"><article><h3>Pair without a camera</h3><p>Copy and enter the same one-time pairing code, with Waydroid guidance and local setup diagnostics. Devices still need a reachable local network.</p></article><article><h3>Offline chapters and transcripts</h3><p>Keep available chapter metadata with new downloads, use recorded companion captions offline and read supported embedded local chapters.</p></article><article><h3>Return to previous playback</h3><p>Restore the previous video, queue, position and play/pause state within the same playback session and privacy scope.</p></article></div><p class="small">These features, Vimeo, optional audio artwork, remaining-time estimates and signed service plugins are included in the reviewed {DATA["release"]} release. {link(p,"releases.html","Read release availability")}</p></section>{cta(p)}</div>'
    emit(p,'Features','Explore WizeStream playback, services, subscriptions, downloads, local profiles, synchronization, themes, learning, TV and radio.',body)


def documentation():
    p='docs/index.html'
    groups=[]
    for doc in DATA['docs']:
        if doc['group'] not in groups: groups.append(doc['group'])
    grouped=''.join(f'<section class="doc-group" data-guide-group><h2>{esc(group)}</h2><div class="guide-grid">'+''.join(doc_card(p,d) for d in DATA['docs'] if d['group']==group)+'</div></section>' for group in groups)
    body=f'<div class="container">{hero("The WizeStream handbook","A guide for every part<br>of your library.","Start with installation, explore the controls, and understand where local data and optional network features meet.")}{notice(p)}<section id="search" class="docs-search-panel"><label for="guide-filter">Find a guide</label><div class="search-field">{icon("search")}<input id="guide-filter" type="search" placeholder="Search playback, backup, radio…" autocomplete="off"></div><p id="guide-filter-status" class="small" aria-live="polite">15 practical guides. Full documentation search is also available from the header.</p></section>{grouped}<div class="help-band"><div><h2>Looking for a quick answer?</h2><p>Start with the FAQ, or collect the right details for a useful bug report.</p></div><div class="button-row">{link(p,"faq.html","Read the FAQ","button button-secondary")}{link(p,"support.html","Get support "+icon("arrow"),"text-link")}</div></div></div>'
    emit(p,'Documentation','Fifteen WizeStream guides covering setup, playback, downloads, local libraries, device sync, learning, themes, privacy and troubleshooting.',body,'docs/index.html')
    doc_sources={
        'getting-started':['README.md#installation','docs/faq.md','docs/nightly-builds.md'],
        'services-discovery':['README.md#supported-services','README.md#streaming-and-discovery','docs/plugins.md'],
        'playback':['README.md#playback','docs/faq.md','app/src/main/res/xml/video_audio_settings.xml'],
        'sponsorblock':['app/src/main/res/xml/sponsor_block_settings.xml','app/src/main/java/org/schabi/newpipe/sponsorblock/SponsorBlockContributions.kt'],
        'devices':['docs/android-auto.md','docs/yt-fifo.md','docs/faq.md'],
        'live-media':['README.md#live-tv','README.md#live-radio'],
        'subscriptions':['README.md#library-subscriptions-and-downloads','docs/progressive-subscription-refresh.md'],
        'downloads':['README.md#library-subscriptions-and-downloads','docs/faq.md','docs/device-synchronization.md'],
        'profiles-sync':['docs/device-synchronization.md','README.md#profiles-and-local-privacy'],
        'backups-migration':['docs/imports/libretube.md','docs/imports/text-playlists.md','app/src/main/java/org/schabi/newpipe/settings/export/FreeTubeTransferFragment.kt'],
        'learning':['docs/learning-mode.md','app/src/main/java/org/schabi/newpipe/learning/LearningDifficulty.kt','app/src/main/java/org/schabi/newpipe/sync/LessonDifficultySyncAdapter.kt'],
        'customization':['README.md#interface-and-customization','docs/faq.md'],
        'theme-packs':['docs/themes/README.md','docs/themes/theme-pack.schema.json'],
        'privacy-network':['PRIVACY.md','docs/invidious-media-servers.md','docs/plugins.md'],
        'troubleshooting':['docs/faq.md','docs/device-synchronization.md','docs/android-auto.md'],
    }
    sidebar=''
    for group in groups:
        sidebar+=f'<p class="sidebar-label">{esc(group)}</p><ul>'+''.join(f'<li data-doc-slug="{d["slug"]}">{{LINK:{d["slug"]}}}</li>' for d in DATA['docs'] if d['group']==group)+'</ul>'
    for i,doc in enumerate(DATA['docs']):
        p=f'docs/{doc["slug"]}.html'
        source=(ROOT/f'content/docs/{doc["slug"]}.md').read_text()
        article,heads=markdown(source)
        toc='<nav class="inline-toc" aria-label="On this page"><strong>On this page</strong><ul>'+''.join(f'<li><a href="#{a}">{esc(t)}</a></li>' for a,t in heads)+'</ul></nav>'
        side=sidebar
        for d in DATA['docs']:
            side=side.replace('{LINK:'+d['slug']+'}',link(p,'docs/'+d['slug']+'.html',esc(d['title']),'is-active' if d['slug']==doc['slug'] else ''))
        sources=''.join(f'<li>{link(p,(REPO+"/blob/pipe/" if src.startswith("docs/") else SOURCE)+src,esc(src)+" ↗")}</li>' for src in doc_sources[doc['slug']])
        previous=DATA['docs'][i-1] if i else None
        following=DATA['docs'][i+1] if i+1<len(DATA['docs']) else None
        pager='<nav class="doc-pager" aria-label="Adjacent guides">'+(link(p,'docs/'+previous['slug']+'.html','<span>Previous guide</span>'+esc(previous['title'])) if previous else '<span></span>')+(link(p,'docs/'+following['slug']+'.html','<span>Next guide</span>'+esc(following['title'])) if following else link(p,'docs/index.html','<span>Keep exploring</span>All documentation'))+'</nav>'
        body=f'<div class="container doc-layout"><aside class="docs-sidebar"><details class="docs-menu" open><summary>Browse documentation</summary><div>{link(p,"docs/index.html","Documentation home","sidebar-home")}{side}</div></details></aside><article class="doc-article"><nav class="breadcrumbs" aria-label="Breadcrumb">{link(p,"index.html","Home")}<span>/</span>{link(p,"docs/index.html","Docs")}<span>/</span><span>{esc(doc["title"])}</span></nav><header class="doc-title"><p class="eyebrow">{esc(doc["group"])}</p><h1>{esc(doc["title"])}</h1><p>{esc(doc["description"])}</p></header>{notice(p)}{toc}<div class="prose">{article}<section class="doc-sources"><h2 id="references">Project references</h2><p>Reviewed against the documented release/source snapshot. Documentation links follow the current repository paths; code links use the reviewed snapshot. Check the release notes for installed-build availability.</p><ul>{sources}</ul></section></div>{pager}</article></div>'
        emit(p,doc['title'],doc['description'],body,'docs/index.html',True,article)


def comparison():
    p='compare.html'
    categories={'services':'Services & community features','playback':'Playback','devices':'Devices & live media','library':'Library & downloads','privacy':'Profiles, privacy & sync','tools':'Tools & maintenance'}
    head='<th scope="col" class="feature-column">Feature</th>'
    for project in DATA['projects']:
        head+=f'<th scope="col" data-project="{project["key"]}" class="{"wize-column" if project["key"]=="wizestream" else ""}">{link(p,project["url"],esc(project["name"]))}<span class="project-version">{esc(project["version"])}</span></th>'
    bodies=''
    for category,label in categories.items():
        group=''
        for row in DATA['comparison']:
            if row['category']!=category: continue
            cells=''
            for project in DATA['projects']:
                cell=row['cells'][project['key']]
                marker={'yes':'✓','partial':'◐','unknown':'?','no':'—','archived':'◷'}[cell['level']]
                next_badge=f'<span class="pill next">After {DATA["release"]}</span>' if cell['next'] else ''
                cells+=f'<td data-project="{project["key"]}" data-level="{cell["level"]}" class="{"wize-column" if project["key"]=="wizestream" else ""}"><span class="cell-status status-{cell["level"]}"><span aria-hidden="true">{marker}</span><span>{esc(cell["text"])}</span></span>{next_badge}</td>'
            group+=f'<tr data-comparison-row data-category="{category}"><th scope="row">{esc(row["feature"])}</th>{cells}</tr>'
        bodies+=f'<tbody data-comparison-group="{category}"><tr class="category-row"><th colspan="6" scope="colgroup">{label}</th></tr>{group}</tbody>'
    source_cards=''
    for group in DATA['comparisonSources']:
        source_cards+='<section class="source-card"><h3>'+esc(group['project'])+'</h3><ul>'+''.join('<li>'+link(p,u,esc(t)+' ↗')+'</li>' for t,u in group['links'])+'</ul></section>'
    options='<option value="all">All projects</option>'+''.join('<option value="'+x['key']+'">WizeStream + '+esc(x['name'])+'</option>' for x in DATA['projects'] if x['key']!='wizestream')
    category_options='<option value="all">All categories</option>'+''.join(f'<option value="{key}">{value}</option>' for key,value in categories.items())
    body=f'<div class="container comparison-container">{hero("A transparent comparison","Different projects.<br>Different strengths.","Compare selected Android features across WizeStream, NewPipe, PipePipe and LibreTube, with Tubular included as a historical reference.")}<div class="comparison-method"><p><strong>WizeStream source reviewed {REVIEWED_DATE}.</strong> Its column covers the reviewed {DATA["release"]} release ({RELEASE_DATE}) at the pinned source commit. Other project columns retain their {COMPARISON_REVIEWED_DATE} review and the named releases; their evidence has not been refreshed with this WizeStream update.</p><p><strong>Not verified is not a No.</strong> It means the reviewed official sources did not establish the capability. This is a feature guide, not a performance benchmark or a ranking of quality.</p><p><strong>Tubular is discontinued.</strong> Its repository was archived in July 2026. Its last release is shown for context, not as an active recommendation.</p></div><form class="comparison-controls" aria-label="Comparison filters"><div><label for="comparison-search">Find a feature</label><input id="comparison-search" type="search" placeholder="Search captions, sync, radio…" autocomplete="off"></div><div><label for="comparison-category">Category</label><select id="comparison-category">{category_options}</select></div><div><label for="comparison-project">Projects</label><select id="comparison-project">{options}</select></div><label class="checkbox-label" for="comparison-differences"><input type="checkbox" id="comparison-differences">Differences only</label><button class="button button-secondary" type="reset">Reset</button></form><div class="table-toolbar"><p id="comparison-status" aria-live="polite">35 features · 5 projects</p><p class="small">Scroll horizontally for more columns. On a phone, choose one competitor to reduce the width.</p></div><div class="comparison-scroll table-scroll" role="region" aria-label="Feature comparison table; horizontally scrollable" tabindex="0"><table id="comparison-table"><caption class="sr-only">Selected Android features; verified releases and evidence are described above and below the table.</caption><thead><tr>{head}</tr></thead>{bodies}</table></div><p id="comparison-empty" class="empty-state" hidden>No features match these filters. Try a broader search or reset the controls.</p><section class="section"><div class="section-heading"><div><p class="eyebrow">Choose for your workflow</p><h2>Where the approaches differ.</h2></div></div><div class="three-columns"><article class="plain-card"><h3>Local libraries and a broad toolkit</h3><p>WizeStream combines multiple services with local profiles, peer-to-peer sync, learning tools, live directories and offline scheduling. Check the guide for each feature’s boundaries.</p></article><article class="plain-card"><h3>Focused alternatives</h3><p>NewPipe offers its upstream experience. PipePipe adds its own service and community tools. LibreTube includes strengths such as DeArrow contributions, buffering controls and optional Piped account sync.</p></article><article class="plain-card"><h3>A fair release comparison</h3><p>A merged development feature is not automatically in a stable APK. Documentation omissions are not proof of absence, and source support can change after this snapshot.</p></article></div></section><section id="comparison-sources" class="section section-top-border"><p class="eyebrow">Evidence, not guesswork</p><h2>Official project sources.</h2><p class="section-intro">Tagged documentation, release notes and relevant source files were used to check the matrix. WizeStream’s {link(p,SOURCE+"README.md","reviewed source snapshot")} and individual guides describe its column.</p><div class="source-grid">{source_cards}</div><p class="small">Related active project: {link(p,"https://github.com/bravepipeproject/BravePipe","BravePipe ↗")}. It is linked separately rather than filling a comparison column with unverified inherited claims.</p></section></div>'
    emit(p,'Compare Android streaming clients','A sourced, release-aware comparison of WizeStream, NewPipe, PipePipe, LibreTube and historical Tubular across 35 features.',body)


def downloads():
    p='downloads.html'
    body=f'<div class="container">{hero("Choose your installation","Ready when you are.","Install the signed stable release, use an F-Droid-compatible client, or keep a separate nightly build for testing.")}<div class="download-primary"><div><h2>For most Android phones.</h2><p>The standard APK supports ARM64 and ARMv7. Android 6.0 or newer is required.</p><div class="button-row">{link(p,"download.php?arch=arm",icon("download")+"Download ARM from WizeStream","button button-primary")}{link(p,LATEST_RELEASE,"Latest stable release on GitHub "+icon("external"),"text-link")}</div><p class="small">The GitHub release includes both the standard ARM APK and the x86_64 APK.</p></div><div class="download-specs"><div><span>Android</span><strong>6.0+</strong></div><div><span>ARM build</span><strong>ARM64 + ARMv7</strong></div><div><span>Account</span><strong>Not required</strong></div><div><span>License</span><strong>GPLv3</strong></div></div></div><div class="three-columns download-options"><article class="plain-card"><span class="card-icon">{icon("archive")}</span><h2>IzzyOnDroid</h2><p>Use an F-Droid-compatible client with the IzzyOnDroid repository. Prefer that client for subsequent updates to the same installation.</p>{link(p,"https://apt.izzysoft.de/packages/org.wisso.newpipematerial","Open the project listing "+icon("external"),"text-link")}</article><article class="plain-card"><span class="card-icon">{icon("sliders")}</span><h2>x86_64 Android</h2><p>For compatible Intel/AMD Android environments, including Waydroid and Android-x86. This is an Android APK, not a native Linux executable.</p><div class="button-row">{link(p,"download.php?arch=x86_64","Download x86_64 from WizeStream "+icon("download"),"text-link")}{link(p,LATEST_RELEASE,"Find the x86_64 APK on GitHub "+icon("external"),"text-link")}</div></article><article class="plain-card"><span class="card-icon">{icon("compass")}</span><h2>Nightly builds</h2><p>A separate, unstable installation for recent development changes. Keep important library data backed up before experimenting.</p>{link(p,"https://github.com/wizdom13/WizeStream_Nightly/releases","Browse nightly builds "+icon("external"),"text-link")}</article></div><section class="download-statistics" data-download-stats data-endpoint="badges/summary.php" aria-labelledby="download-statistics-title"><div class="section-heading"><div><p class="eyebrow">Across every official route</p><h2 id="download-statistics-title">WizeStream downloads.</h2></div><p>Independent totals from IzzyOnDroid, GitHub stable and nightly releases, and direct website downloads.</p></div><div class="download-stat-grid"><a class="download-stat-card" href="https://dlstats.izzyondroid.org/#app-trends?app=org.wisso.newpipematerial&amp;clients=_total&amp;agg=daily" rel="noreferrer"><span>IzzyOnDroid</span><strong data-download-stat="izzy">—</strong><small>downloads</small></a><a class="download-stat-card" href="https://github.com/wizdom13/WizeStream/releases" rel="noreferrer"><span>GitHub</span><strong data-download-stat="github">—</strong><small>stable + nightly</small></a><a class="download-stat-card" href="download.php?arch=arm"><span>WizeStream.org</span><strong data-download-stat="website">—</strong><small>direct downloads</small></a><a class="download-stat-card download-stat-total" href="downloads.html"><span>Total downloads</span><strong data-download-stat="total">—</strong><small>all three sources</small></a></div><p class="small download-stat-status" data-download-stats-status aria-live="polite">Loading privacy-respecting aggregate totals…</p></section><section class="section two-column-copy"><div><p class="eyebrow">A few useful details</p><h2>Install once.<br>Update consistently.</h2><p>WizeStream keeps the historical package ID <code>org.wisso.newpipematerial</code> so existing users can update after its rename. Official NewPipe has a different app ID and can coexist.</p></div><div class="prose"><h3>Keep the same source</h3><p>Use the same installation/update source whenever possible. A signature mismatch should be investigated before removing an app that contains your local library.</p><h3>Move your data deliberately</h3><p>Export an original backup before importing from NewPipe or another supported format. Verify subscriptions and playlists before discarding the source file.</p><h3>Know the permission</h3><p>Android’s permission to install an APK belongs to the browser or file manager handling it. Popup, notifications and local-media access are separate feature permissions.</p>{link(p,"docs/getting-started.html","Follow the installation guide "+icon("arrow"),"text-link")}</div></section><section class="help-band"><div><h2>Other project routes.</h2><p>GitHub Store provides another client option. A separate native Desktop project has its own code and release path.</p></div><div class="button-row">{link(p,"https://github-store.org/app?repo=wizdom13/WizeStream","GitHub Store ↗","button button-secondary")}{link(p,"https://github.com/wizdom13/WizeStream-Desktop","WizeStream Desktop ↗","button button-secondary")}</div></section></div>'
    emit(p,'Download WizeStream','Install WizeStream for Android from GitHub or IzzyOnDroid. Find ARM, x86_64 and separate nightly builds with clear setup instructions.',body)


FAQ = [
 ('The basics','What is WizeStream?','An independent, NewPipe-based Android streaming client with a Material 3-oriented interface, flexible playback, local library tools and support for multiple sources.'),
 ('The basics','Is this official NewPipe?','No. WizeStream is not affiliated with, sponsored by or endorsed by TeamNewPipe or NewPipe e.V. It preserves upstream attribution and open-source licensing.'),
 ('The basics','Do I need a Google or WizeStream account?','Normal account-free browsing and playback do not require either. Your subscriptions and local playlists belong to the app’s library rather than a Google account.'),
 ('The basics','What Android version do I need?','Android 6.0 or newer for the app and built-in services; external service plugins require Android 7.0 or newer. Use the normal ARM APK for ARM64/ARMv7 devices and the separate x86_64 APK for compatible Intel/AMD Android environments.'),
 ('The basics','Does it need Google Play Services?','WizeStream’s normal account-free functions do not require Google Play Services. Optional integrations can depend on Android features or separately installed applications.'),
 ('Installation','Where should I download it?','Use the [download page](downloads.html) for GitHub releases, IzzyOnDroid and other project links. Keep the same update source whenever possible.'),
 ('Installation','Why does the app ID still say newpipematerial?','The stable identity is retained so existing NewPipe Material installations can update to WizeStream without losing their app data.'),
 ('Installation','Can stable, nightly and official NewPipe coexist?','They use different application identities. Stable WizeStream, its separate nightly build and official NewPipe can be installed alongside one another.'),
 ('Installation','Is the x86_64 APK a native Linux app?','No. It is an Android APK for compatible environments such as Waydroid and Android-x86. The separately linked Desktop project has its own implementation.'),
 ('Installation','Why does a guide mention a setting I cannot see?','This handbook was checked against 1.52.2. An older installed build, Android requirement or disabled optional feature can hide a setting. Check [release availability](releases.html).'),
 ('Installation','Can I install another streaming service?','Since 1.52.0, Settings → Plugins installs signed native WizeStream API 2.0 or compatible PipePipe API 1.0/1.1 packages. External plugins need Android 7+ and a trusted publisher; packages and configuration stay on the device. See [service plugins](docs/services-discovery.html#add-a-service-plugin).'),
 ('Playback','Can I listen with the screen off?','Use supported background audio. Listen mode also continues the current item as audio while retaining its queue and position.'),
 ('Playback','Does the visualizer record my microphone?','No. The player visualizer uses decoded media audio and does not require microphone access.'),
 ('Playback','How do popup and Android PiP differ?','Popup is WizeStream’s floating player. Native PiP is a separate Android 8+ system feature. They have different settings and permissions.'),
 ('Playback','Can I repeat a passage or adjust captions?','Supported non-live playback offers A–B repeat and session-only subtitle timing. The independent A–B enable switch is off by default and has been available since 1.42.0, including 1.52.2. See [playback](docs/playback.html).'),
 ('Playback','Is every video available in 360°?','No. Genuine spherical content and correct projection information are needed for a meaningful 360° experience. Forcing a flat video into a projection does not create a spherical recording.'),
 ('Playback','Can I rewind every live stream?','Only as far as the broadcaster’s available DVR history allows. A stream can end, change formats or stop serving historical segments.'),
 ('Your library','Do subscriptions change my YouTube account?','No. They are stored locally in WizeStream. You do not need to sign in to maintain a local subscription library.'),
 ('Your library','Why is an upload missing from What’s New?','Check group, service, content/watch-state, date and blocking filters, then refresh. Feeds are not unlimited historical archives. Use the [feed guide](docs/subscriptions.html).'),
 ('Your library','Can I import a NewPipe backup?','Compatible backups are supported through Settings → Backup and restore. Keep the original export until you have verified the imported result.'),
 ('Your library','Which builds support FreeTube transfer?','FreeTube subscription and playlist transfer is available since 1.42.0, including the reviewed 1.52.2 release. See the [migration guide](docs/backups-migration.html) for supported file formats.'),
 ('Your library','Can I pair Waydroid without a camera?','The reviewed release includes Copy pairing code and Enter pairing code. Copy the phone’s code into Waydroid while the phone’s pairing screen remains open, then start Sync now in Waydroid. The code expires after five minutes; a reachable local network is still required. See [profiles and synchronization](docs/profiles-sync.html).'),
 ('Your library','Does Device Sync copy video files?','No. It can transfer supported completed-download metadata. Missing media remains Not local and must be fetched again or copied separately.'),
 ('Your library','Is sync a backup?','No. Synchronization merges supported records between trusted devices. Keep an independent export for portable recovery.'),
 ('Your library','Are all settings separated by profile?','No. Profiles separate supported library records, but some settings and data categories remain app-wide. Review the relevant guide and sync controls.'),
 ('Downloads','Are companion captions embedded in the video?','No. The supported companion-caption workflow queues a separate sidecar subtitle file with a full video download.'),
 ('Downloads','Why did a scheduled download not start exactly on time?','A download window permits transfers during an interval; it is not an exact alarm. Android scheduling, battery, network and charging constraints can delay work.'),
 ('Downloads','Can offline cleanup remove every file on my phone?','The optional retention workflow is limited to tracked automatic downloads and excludes supported protected, manual/imported and changed-size files. Review its candidate preview.'),
 ('Privacy','Does Incognito hide my IP address?','No. It pauses supported activity recording. Services, proxies and network providers can still receive normal connection information.'),
 ('Privacy','Are SponsorBlock contributions anonymous?','No. They are explicit public community actions using a persistent private contribution identity, with normal network connection information. The ID is excluded from normal WizeStream backups and Device Sync.'),
 ('Privacy','Why is Translate missing on a comment?','The feature needs Android 12+, an already-ready on-device language pair, and suitable language detection. WizeStream does not download a model or fall back to cloud translation.'),
 ('Privacy','Does this website track documentation searches?','Search runs in your browser and sends no query to a search server. This site includes no analytics, third-party fonts or tracking cookies. A chosen light/dark preference may be stored locally.'),
 ('Devices & themes','Does Android Auto show videos?','No. WizeStream’s Auto integration is audio-oriented. Debug or nightly builds can also be subject to Android Auto’s app-discovery rules.'),
 ('Devices & themes','Why can a cast receiver fail on a video that plays on my phone?','A receiver can support different codecs or require a compatible source URL. Not every service stream exposes a castable format.'),
 ('Devices & themes','Are theme packs executable plugins?','No. They are validated data-only JSON packs, with bounded embedded PNG artwork where supported. Scripts and remote assets are outside the supported format.'),
 ('Devices & themes','Does Glass style every dialog?','No. It applies to supported navigation surfaces and layouts, with opaque fallbacks in some runtime conditions. Static listening layouts are not animated mechanical players.'),
 ('Help','Where should I report an issue?','Report a WizeStream problem to WizeStream first, including extractor or service failures. Use [Support](support.html), include a public example and your app/Android versions, and remove sensitive data from logs.'),
]


def faq():
    p='faq.html'; groups=[]
    for group,*_ in FAQ:
        if group not in groups: groups.append(group)
    body=f'<div class="container narrow-page">{hero("Quick answers","A little clarity<br>goes a long way.","Common questions about installation, playback, local data, devices and privacy.")}<div class="faq-search"><label for="faq-filter">Find an answer</label><input id="faq-filter" type="search" placeholder="Search questions and answers…" autocomplete="off"><p id="faq-filter-status" class="small" aria-live="polite">{len(FAQ)} answers</p></div>'
    for group in groups:
        body+=f'<section class="faq-group" data-faq-group><h2>{esc(group)}</h2>'
        for _,question,answer in [q for q in FAQ if q[0]==group]:
            body+=f'<details class="faq-item"><summary>{esc(question)}<span aria-hidden="true">+</span></summary><div><p>{inline(answer)}</p></div></details>'
        body+='</section>'
    body+=f'<section class="help-band"><div><h2>Need more detail?</h2><p>The handbook explains the workflows and their limits step by step.</p></div>{link(p,"docs/index.html","Open the handbook "+icon("arrow"),"text-link")}</section></div>'
    emit(p,'Frequently asked questions','Answers to common WizeStream questions about accounts, installation, playback, downloads, sync, privacy, themes and support.',body)


def privacy():
    p='privacy.html'
    body=f'<div class="container narrow-page">{hero("A readable privacy model","Local-first.<br>Clear about connections.","Understand what stays on your device, when optional services are contacted, and how this website behaves.")}<div class="prose"><h2 id="app">The Android application</h2><p>WizeStream does not require a WizeStream account and does not include advertising, analytics or tracking SDKs. Subscriptions, playlists, supported profile data and history are held locally or exported to a destination you choose.</p><p>This is a data-storage and feature-choice model, not a claim of network anonymity. Media services and network providers can receive normal connection information while you stream. Incognito pauses supported activity recording; it is not a VPN.</p><h2 id="optional-services">Optional services remain choices</h2><p>Update checks contact GitHub when enabled or requested. SponsorBlock, DeArrow and dislike features contact their community providers when used. Live Radio and Live TV browse external directories and connect to the selected stream source.</p><p>SponsorBlock contributions require a separate opt-in and use a persistent private contribution ID. Submissions and votes can become public. The contribution identity is excluded from normal WizeStream backups and Device Sync; it should not be posted in a report.</p><p>Voice search delegates to an installed recognition provider. Although WizeStream requests offline recognition, the provider determines whether it can work locally. On-device comment translation has different requirements and no WizeStream cloud fallback.</p><h2 id="sharing">Sync, casting and deliberate sharing</h2><p>Device Sync exchanges supported records through encrypted peer-to-peer connections after one-time pairing. The reviewed release supports QR scanning or copying and entering the same code manually. Search history is excluded unless enabled on both devices. Downloaded media files are not transferred.</p><p>Casting and configured home-audio handoff send selected media information to your chosen receiver or server, which may then request the source media itself. A personal backup or imported playlist can also contain private information even though the app has no cloud account.</p><h2 id="reports">Reports you can review</h2><p>WizeStream does not automatically upload crash reports. Its reporting flow lets you review and edit information before copying it or sending it through an email app or GitHub.</p><p>Reports can include URLs, service/language details, versions, exceptions and your own text. Remove passwords, authentication cookies, private links and personal database contents before posting to a public project.</p><h2 id="website">This website</h2><p>The documentation website uses static pages; the optional community reviews page uses PHP and MySQL. Neither includes analytics scripts, advertising scripts, tracking cookies, third-party fonts or embedded social-media widgets. Documentation search and comparison filters run inside your browser without sending their input to a search or analytics service.</p><p>If you explicitly choose a light or dark appearance, the website stores that preference locally as <code>wizestream-site-theme</code>. The review page can also remember a private review management link only when you explicitly opt in; this browser-only key lets anyone with this browser profile edit or delete the review, is not synchronized to other devices, and can be cleared in My Reviews or your browser’s site-data settings. Otherwise no review management key is saved on the device. Ordinary access still reveals connection information to the hosting and network providers; their logging practices depend on the final hosting configuration.</p><p>The direct-download route records only aggregate ARM and x86_64 request counts. It does not store IP addresses, user agents, referrers, cookies or device identifiers. HEAD, prefetch and resumed range requests are excluded. Public download badges combine that website total with public GitHub release-asset counts and public IzzyOnDroid aggregate statistics.</p><p>Images and interface assets are served with the website. External GitHub, IzzyOnDroid, competitor and project-reference links are contacted when you follow them, rather than being loaded as third-party widgets. Those destinations have their own policies.</p><h2 id="community-reviews">Optional community reviews</h2><p>The separate <a href="reviews/">community review page</a> asks for a rating, written feedback, consent, and optional nickname and app version. It requires no account or email. Every submission waits for moderation; only published reviews count toward the public rating.</p><p>The PHP service stores a hash of your private deletion key, not the key itself. Temporary HMAC-derived address buckets in MySQL enforce shared abuse limits across PHP workers. Identifiers change each UTC hour, expire at the next UTC hour and are removed on the next request or hourly cleanup. They are not attached to a review. Raw addresses, user agents and device IDs are not stored by this service.</p><p>Pending reviews expire after 30 days and are cleaned up on the next request or scheduled cleanup. Published reviews stay until the author or moderator removes them. Deletion removes the live record; hosting logs, database logs and backups follow the operator’s actual configuration. The service does not guarantee one review per person or verified installations.</p><h2 id="controls">Useful controls and references</h2><ul><li>Review history, Incognito, optional community features and selected network settings in the app.</li><li>Keep private backups and contribution/review management secrets out of public issues.</li><li>Use the {link(p,"docs/privacy-network.html","privacy and networking guide")} for proxies, Invidious media-host trust and individual feature boundaries.</li><li>Read the {link(p,SOURCE+"PRIVACY.md","repository privacy policy")} for the app’s canonical published policy.</li></ul><p class="small">This overview was reviewed on {REVIEWED_DATE} against the documented source snapshot. Newly merged behavior may be newer than an installed stable build or an older policy section; the linked source and feature guides provide that context.</p></div></div>'
    emit(p,'Privacy','A plain-language explanation of WizeStream local data, optional network services, manual reporting and this static website’s privacy behavior.',body)


def support():
    p='support.html'
    body=f'<div class="container">{hero("Help the project move forward","Useful feedback.<br>Better releases.","Find an answer, report a reproducible problem, or contribute to the independent WizeStream project.")}<div class="three-columns"><article class="plain-card"><span class="card-icon">{icon("book")}</span><h2>Read a guide</h2><p>Setup, library behavior, controls and common limitations are documented in the handbook and FAQ.</p>{link(p,"docs/index.html","Browse documentation "+icon("arrow"),"text-link")}</article><article class="plain-card"><span class="card-icon">{icon("help")}</span><h2>Report a bug</h2><p>Give a public example, installed version, expected behavior and steps to reproduce. Report WizeStream failures to this project first.</p>{link(p,REPO+"/issues/new?template=bug_report.yml","Open the bug form "+icon("external"),"text-link")}</article><article class="plain-card"><span class="card-icon">{icon("compass")}</span><h2>Discuss an idea</h2><p>Explain the workflow you want to improve or join a project discussion. Posting on GitHub requires a GitHub account.</p>{link(p,REPO+"/discussions","Open Discussions "+icon("external"),"text-link")}</article></div><section class="section two-column-copy"><div><p class="eyebrow">Before opening an issue</p><h2>Make the problem<br>easy to understand.</h2></div><div class="prose"><ol><li>Check the installed version and whether you are using stable, nightly or a PR/debug build.</li><li>Identify the selected service, optional instance/proxy and a public example URL.</li><li>Write the exact action, expected result and observed result.</li><li>Include Android/device details and relevant playback or filter state.</li><li>Review any log before sharing it. Remove cookies, credentials, private URLs and personal data.</li></ol><p>Do not upload a full private database just to demonstrate a missing item. For sensitive material, ask the maintainer for an appropriate private route before posting it.</p>{link(p,"docs/troubleshooting.html","Use the troubleshooting checklist "+icon("arrow"),"text-link")}</div></section><section class="section section-top-border"><p class="eyebrow">Ways to contribute</p><h2>Code is one of many useful contributions.</h2><div class="three-columns"><article class="plain-card"><h3>Focused fixes and testing</h3><p>Reproduction steps, device checks, playback fixes, accessibility improvements and well-scoped code changes help maintainers review behavior clearly.</p>{link(p,REPO+"/blob/pipe/.github/CONTRIBUTING.md","Contribution guide ↗","text-link")}</article><article class="plain-card"><h3>Documentation and translations</h3><p>Clear instructions and native-speaker-reviewed translations improve the experience without adding a new service dependency.</p>{link(p,REPO,"Explore the repository ↗","text-link")}</article><article class="plain-card"><h3>Accessible community themes</h3><p>Build within the documented data-only format, preserve native controls and share compatible artwork with appropriate licensing.</p>{link(p,"docs/theme-packs.html","Theme pack guide "+icon("arrow"),"text-link")}</article></div></section><section class="developer-panel"><p class="eyebrow">Build from source</p><h2>Open code. Integrated extractor.</h2><p>The app and its integrated extractor source are compiled in the same repository. Start with Git, JDK 21 and the Android SDK requirements in the maintained build guide.</p><pre><code>git clone https://github.com/wizdom13/WizeStream.git\ncd WizeStream\nscripts/build.sh debug</code></pre><div class="button-row">{link(p,REPO+"/blob/pipe/BUILDING.md","Read BUILDING.md "+icon("external"),"button button-secondary")}{link(p,REPO+"/issues/new?template=feature_request.yml","Feature request form ↗","text-link")}</div></section></div>'
    emit(p,'Support and contribute','Get WizeStream help, report a reproducible bug, discuss a feature, create an accessible theme or build the open-source app.',body)


def about():
    p='about.html'
    body=f'<div class="container narrow-page">{hero("The independent project","A familiar foundation.<br>Its own direction.","WizeStream builds on NewPipe’s libre software foundation while developing its own playback, local library, interface and service-support work.")}<div class="prose"><h2 id="independent">Independent, with upstream credit</h2><p>WizeStream is maintained as an independent NewPipe-based Android application by WizDom13. It is not affiliated with, sponsored by or endorsed by the official NewPipe project, TeamNewPipe or NewPipe e.V.</p><p>Material 3-oriented design is one part of its purpose. The app also develops local profiles, device synchronization, offline organization, learning tools, live media and coordinated player/extractor changes.</p><h2 id="lineage">An integrated codebase</h2><p>The bundled extractor source is derived from NewPipeExtractor and later incorporated PipePipeExtractor and WizeStream-specific changes. It is maintained inside the app repository, rather than being presented as an unchanged upstream binary dependency.</p><p>WizeStream accepts responsibility for its integrated changes. Problems seen in WizeStream should be reported to WizeStream first; an upstream comparison can help investigate a shared issue without moving the report away from this project.</p><h2 id="name">Why the name changed</h2><p>The app was previously called NewPipe Material. The WizeStream name distinguishes this independently maintained application from official NewPipe. The historical Android package identity remains unchanged so users can continue receiving compatible signed updates without resetting their local data.</p><h2 id="license">Open-source licensing</h2><p>WizeStream preserves the GPLv3 license, upstream attribution and third-party notices. The repository and in-app license section are the references for source and component licensing.</p><p>{link(p,REPO+"/blob/pipe/LICENSE","Read the project license ↗")} · {link(p,REPO+"/blob/pipe/UPSTREAM.md","See the tracked upstream baseline ↗")}</p><h2 id="credits">People and projects behind the work</h2><ul><li>NewPipe contributors and the upstream libre software foundation.</li><li>PipePipeExtractor contributors and the project-specific integrated service work.</li><li>Fabián PS (@FabianOvrWrt), who contributed WizeStream’s logo graphics.</li><li>IzzySoft and IzzyOnDroid for distribution, independent reproducibility work and diagnostics.</li><li>Community members who report reproducible problems, review changes, translate, test and share accessible themes.</li></ul><p>{link(p,REPO+"/pull/97","Logo contribution ↗")} · {link(p,"https://github.com/TeamNewPipe/NewPipe","NewPipe ↗")} · {link(p,"https://apt.izzysoft.de/packages/org.wisso.newpipematerial","IzzyOnDroid listing ↗")}</p><h2 id="website">About this website</h2><p>This multipage handbook uses static pages and locally served assets, with a separate optional PHP community review service. Search and comparison controls operate in your browser. There is no analytics integration or third-party review widget. The linked review form is hosted with the project website.</p><p>Product screenshots are drawn from the project repository; themes, layout and available actions can vary with the app build. The comparison names its checked competitor releases and marks unknown capabilities rather than assuming they are absent.</p></div></div>'
    emit(p,'About WizeStream','Learn about WizeStream’s independent direction, NewPipe lineage, licensing, integrated extractor, credits and website documentation.',body)


def releases():
    p='releases.html'
    cards=[
      ('1.52.2','Latest reviewed release (' + RELEASE_DATE + ')',['Subscription editing controls scroll with the channels above the mini-player; channel-group Grid/List choices persist.','Consistent feed pull-to-refresh, compact two-row filters and themed publication-date buttons.','Second-caption picker, cue-boundary timing and UI restoration fixes; resilient YouTube Charts groups.']),
      ('1.50.0','Earlier stable milestone',['Optional second captions, reorderable portrait player actions and landscape live chat.','Opt-in live-chat capture and offline replay, saved-chat badges and cached viewed comments.','Expanded History filters, Home/feed navigation and settings previews; playback and subscription-control improvements.']),
      ('1.49.0','Earlier stable milestone',['Live-DVR seeking, compact landscape layouts and queue recovery improvements.','Older cached feed pages, expanded watch-history filters and view-only reverse local-playlist sorting.','Paired-device playback, selective settings imports and clearer HTTP failure messages.']),
      ('1.42.0','Earlier stable milestone',['FreeTube subscription/playlist transfer and per-profile local likes.','Optional full-video SponsorBlock labels and opt-in submissions and voting.','Independent off-by-default 360° and A–B enable switches.']),
      ('1.41.0','Earlier stable milestone',['360° video projection controls, panning, optional motion tracking and Recenter.','Live TV with directory browsing, M3U/HTTPS sources, filters, favorites and alternative streams.','Search loaded comments, optional yt-fifo/Snapcast handoff, radio metadata history and playback/interface fixes.']),
      ('1.40.1','Earlier stable milestone',['Optional Glass navigation styling and supported feed blur.','Local Media playlist/tracks navigation improvements.','Release compiler reproducibility-related fixes and diagnostics.']),
      ('1.40.0','Earlier stable milestone',['Live Radio discovery, favorites and custom supported streams.','Searchable station filters and profile-aware remembered choices.','What’s New date filtering and related refresh improvements.']),
      ('1.39.0','Earlier stable milestone',['Combined content/watch-state list filters.','Voice-recognition compatibility and compact landscape/DeX cards.','Playback, feed and sync handling improvements.']),
    ]
    timeline=''.join('<article class="release-card"><div><span class="pill">'+label+'</span><h2>WizeStream '+version+'</h2></div><ul>'+''.join('<li>'+esc(x)+'</li>' for x in changes)+'</ul>'+link(p,REPO+'/releases/tag/v'+version,'Release page '+icon('external'),'text-link')+'</article>' for version,label,changes in cards)
    body=(
        f'<div class="container narrow-page">{hero("Know what is in your build","Release notes.","Find the current stable APKs and their release notes on GitHub.")}'
        f'<div class="button-row">{link(p,LATEST_RELEASE,"Latest stable release "+icon("external"),"button button-primary")}</div>{notice(p)}'
        f'<section class="next-release"><h2>Reviewed release and source.</h2>'
        f'<p>The {link(p,RELEASE_URL,"published v"+DATA["release"]+" release")} and '
        f'{link(p,REPO+"/tree/"+DATA["sourceCommit"],"source reviewed "+REVIEWED_DATE)} '
        f'resolve to <code>{DATA["sourceCommit"][:8]}</code>. The features in this snapshot are included in that APK. '
        'The release also includes signed service plugins, optional floating navigation, manual device pairing, Vimeo support and the documented offline features. Later commits and open pull requests are outside this review.</p>'
        '<p>Recent Home memory, playback and Device Sync changes do not establish that every reported failure is resolved. '
        'Use the release notes and the relevant guide when checking an installed build.</p></section>'
        f'<div class="release-timeline">{timeline}</div><section class="help-band"><div><h2>Need the complete history?</h2>'
        '<p>This page summarizes recent milestones. The repository maintains the detailed changelog and all published APK releases.</p></div>'
        f'<div class="button-row">{link(p,REPO+"/blob/pipe/docs/changelogs.md","Full changelog ↗","button button-secondary")}'
        f'{link(p,REPO+"/releases","All releases ↗","text-link")}</div></section></div>'
    )
    emit(p,'Release notes and availability','Understand WizeStream stable releases, recent development changes and which documented capabilities may require a newer build.',body)


def screenshots():
    p='screenshots.html'
    images=[('playback','Playback and comments','Video details with background, popup and download actions.'),('channel','Channel browsing','Local subscription, channel tabs and supported stream filters.'),('history','History and progress','A history view with watch progress and content filters.'),('playlists','Bookmarked playlists','Saved playlist entries inside the local library.')]
    gallery=''.join(f'<figure class="screenshot-card"><div class="phone">{photo(p,name,alt)}</div><figcaption><h2>{title}</h2><p>{alt}</p></figcaption></figure>' for name,title,alt in images)
    body=f'<div class="container">{hero("A look inside","Familiar controls.<br>A flexible library.","Real screenshots from the WizeStream repository, shown without recreating or inventing app screens.")}<aside class="availability"><span class="notice-icon">{icon("help")}</span><p>These repository screenshots show a particular theme and build. Layout, wording and available actions may differ in newer releases or on your device.</p></aside><div class="screenshot-gallery">{gallery}</div>{cta(p)}</div>'
    emit(p,'Screenshots','Explore real WizeStream repository screenshots of playback, channels, history and bookmarked playlists.',body)


def reviews():
    p='reviews/index.php'
    fragment=(ROOT/'content/reviews.fragment').read_text()
    body='<div class="container review-app">'+hero('Optional community feedback','Community reviews.','Share your experience with WizeStream. No account or email required. Every rating is moderated under the same rules.')+fragment+'</div>'
    emit(p,'Community reviews','Read published WizeStream feedback or leave an optional review without an account.',body,php=True,scripts=('reviews/reviews.js',))


def finish():
    index='window.WizeStreamSearchIndex = '+json.dumps(SEARCH,ensure_ascii=False,separators=(',',':')).replace('</','<\\/')+';\n'
    (ROOT/'assets/search-index.js').write_text(index,encoding='utf8')
    manifest={'pages':PAGES,'sourceCommit':DATA['sourceCommit'],'reviewed':DATA['reviewed'],'release':DATA['release'],'releaseDate':DATA['releaseDate'],'releaseCommit':DATA['releaseCommit'],'comparisonReviewed':DATA['comparisonReviewed'],'baseUrl':BASE_URL}
    (ROOT/'build-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    robots='User-agent: *\nAllow: /\n'
    sitemap=ROOT/'sitemap.xml'
    if BASE_URL:
        robots+='Sitemap: '+urljoin(BASE_URL,'sitemap.xml')+'\n'
        sitemap.write_text('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">'+''.join('<url><loc>'+esc(urljoin(BASE_URL,p.removesuffix('index.php') if p.endswith('index.php') else p))+'</loc></url>' for p in PAGES)+'</urlset>\n')
    elif sitemap.exists():
        sitemap.unlink()
    (ROOT/'robots.txt').write_text(robots)
    print(f'Built {len(PAGES)} pages, {len(DATA["docs"])} guides and {len(DATA["comparison"])} comparison rows.')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url',default=DATA.get('baseUrl', ''),help='Actual HTTPS website origin/path for canonical links and sitemap; omit until hosting is known.')
    args=parser.parse_args()
    if args.base_url:
        url=urlsplit(args.base_url)
        if url.scheme!='https' or not url.hostname or url.username or url.password or url.query or url.fragment:
            parser.error('--base-url must be an actual HTTPS website URL without credentials, query or fragment.')
        BASE_URL=args.base_url.rstrip('/')+'/'
    for build in [home,features,documentation,comparison,downloads,faq,privacy,support,about,releases,screenshots,reviews]:
        build()
    finish()
