<?php
// SPDX-License-Identifier: GPL-3.0-or-later
declare(strict_types=1);

define('WIZE_DOWNLOADS_ENTRY', true);
require_once __DIR__ . '/badges/_common.php';

$architecture = $_GET['arch'] ?? '';
$files = [
    'arm' => 'downloads/wizestream-arm.apk',
    'x86_64' => 'downloads/wizestream-x86_64.apk',
];
if (!is_string($architecture) || !isset($files[$architecture])) {
    http_response_code(400);
    header('Content-Type: text/plain; charset=utf-8');
    echo 'Choose arch=arm or arch=x86_64.';
    exit;
}

$relativePath = $files[$architecture];
if (!is_file(__DIR__ . DIRECTORY_SEPARATOR . str_replace('/', DIRECTORY_SEPARATOR, $relativePath))) {
    http_response_code(503);
    header('Content-Type: text/plain; charset=utf-8');
    header('Cache-Control: no-store');
    echo 'The direct download is temporarily unavailable. Please use the GitHub release page.';
    exit;
}

$method = strtoupper((string) ($_SERVER['REQUEST_METHOD'] ?? 'GET'));
$purpose = strtolower((string) ($_SERVER['HTTP_SEC_PURPOSE'] ?? $_SERVER['HTTP_PURPOSE'] ?? ''));
if ($method === 'GET' && !isset($_SERVER['HTTP_RANGE']) && !str_contains($purpose, 'prefetch')) {
    try {
        wize_increment_website($architecture);
    } catch (Throwable) {
        http_response_code(503);
        header('Content-Type: text/plain; charset=utf-8');
        header('Cache-Control: no-store');
        echo 'The download counter is temporarily unavailable. Please use the GitHub release page.';
        exit;
    }
}

header('Cache-Control: no-store');
header('Referrer-Policy: no-referrer');
header('X-Content-Type-Options: nosniff');
header('Location: ' . $relativePath, true, 302);
exit;
