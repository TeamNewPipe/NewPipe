<?php
// SPDX-License-Identifier: GPL-3.0-or-later
declare(strict_types=1);

define('WIZE_DOWNLOADS_ENTRY', true);
require_once __DIR__ . '/_common.php';

try {
    wize_json_headers();
    echo json_encode(wize_download_summary() + ['updatedAt' => gmdate(DATE_ATOM)], JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
} catch (Throwable) {
    http_response_code(503);
    wize_json_headers('no-store');
    echo '{"error":"Download statistics are temporarily unavailable."}';
}
