<?php
// SPDX-License-Identifier: GPL-3.0-or-later
declare(strict_types=1);

define('WIZE_DOWNLOADS_ENTRY', true);
require_once __DIR__ . '/_common.php';

wize_badge(
    static fn (): int => wize_website_totals()['total'],
    'Website downloads',
    'c62026',
    WIZE_DYNAMIC_BADGE_CACHE_SECONDS
);
