<?php
// SPDX-License-Identifier: GPL-3.0-or-later
declare(strict_types=1);

if (!defined('WIZE_DOWNLOADS_ENTRY')) {
    http_response_code(404);
    exit;
}

const WIZE_BADGE_CACHE_SECONDS = 21600;
const WIZE_DYNAMIC_BADGE_CACHE_SECONDS = 300;
const WIZE_PACKAGE_NAME = 'org.wisso.newpipematerial';
const WIZE_IZZY_YEARLY_BASE = 'https://dlstats.izzyondroid.org/iod-stats-collector/stats/basic/yearly/';
const WIZE_GITHUB_API_BASE = 'https://api.github.com/repos/';
const WIZE_NIGHTLY_ARCHIVE_URL = 'https://raw.githubusercontent.com/wizdom13/WizeStream_Nightly/main/download-stats.json';

function wize_state_path(): string
{
    $configured = getenv('WIZESTREAM_DOWNLOADS_STATE');
    $path = is_string($configured) && $configured !== ''
        ? $configured
        : '/home/i4f32jjkbvgk/wizestream-private/downloads-state.json';
    if (str_contains($path, "\0")) {
        throw new RuntimeException('Invalid download state path.');
    }

    $directory = realpath(dirname($path));
    if ($directory === false || !is_dir($directory) || !is_writable($directory)) {
        throw new RuntimeException('The private download state directory is not writable.');
    }

    $documentRoot = realpath($_SERVER['DOCUMENT_ROOT'] ?? '');
    if ($documentRoot !== false) {
        $root = rtrim($documentRoot, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR;
        $candidate = rtrim($directory, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR;
        if ($candidate === $root || str_starts_with($candidate, $root)) {
            throw new RuntimeException('Keep the download state outside the public document root.');
        }
    }

    return $directory . DIRECTORY_SEPARATOR . basename($path);
}

function wize_default_state(): array
{
    return [
        'schemaVersion' => 1,
        'website' => ['arm' => 0, 'x86_64' => 0],
        'cache' => [],
    ];
}

function wize_decode_state(string $json): array
{
    if (trim($json) === '') {
        return wize_default_state();
    }
    $state = json_decode($json, true, 512, JSON_THROW_ON_ERROR);
    if (!is_array($state)) {
        throw new RuntimeException('Invalid download state.');
    }
    $state += wize_default_state();
    if (!is_array($state['website']) || !is_array($state['cache'])) {
        throw new RuntimeException('Invalid download state.');
    }
    $state['website'] += ['arm' => 0, 'x86_64' => 0];
    return $state;
}

function wize_read_state(): array
{
    $handle = fopen(wize_state_path(), 'c+');
    if ($handle === false) {
        throw new RuntimeException('Unable to open the private download state.');
    }
    try {
        if (!flock($handle, LOCK_SH)) {
            throw new RuntimeException('Unable to lock the private download state.');
        }
        rewind($handle);
        $contents = stream_get_contents($handle);
        if ($contents === false) {
            throw new RuntimeException('Unable to read the private download state.');
        }
        return wize_decode_state($contents);
    } finally {
        flock($handle, LOCK_UN);
        fclose($handle);
    }
}

function wize_update_state(callable $update): mixed
{
    $handle = fopen(wize_state_path(), 'c+');
    if ($handle === false) {
        throw new RuntimeException('Unable to open the private download state.');
    }
    try {
        if (!flock($handle, LOCK_EX)) {
            throw new RuntimeException('Unable to lock the private download state.');
        }
        rewind($handle);
        $contents = stream_get_contents($handle);
        if ($contents === false) {
            throw new RuntimeException('Unable to read the private download state.');
        }
        $state = wize_decode_state($contents);
        $result = $update($state);
        $encoded = json_encode($state, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR) . "\n";
        rewind($handle);
        if (!ftruncate($handle, 0) || fwrite($handle, $encoded) === false || !fflush($handle)) {
            throw new RuntimeException('Unable to write the private download state.');
        }
        return $result;
    } finally {
        flock($handle, LOCK_UN);
        fclose($handle);
    }
}

function wize_http_json(string $url, bool $allowMissing = false): ?array
{
    $headers = [
        'Accept: application/vnd.github+json, application/json',
        'User-Agent: WizeStream-download-badges/1.0 (+https://wizestream.org)',
        'X-GitHub-Api-Version: 2022-11-28',
    ];
    $status = 0;
    $body = false;

    if (function_exists('curl_init')) {
        $curl = curl_init($url);
        if ($curl === false) {
            throw new RuntimeException('Unable to initialize the HTTP client.');
        }
        curl_setopt_array($curl, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_FOLLOWLOCATION => true,
            CURLOPT_MAXREDIRS => 3,
            CURLOPT_CONNECTTIMEOUT => 5,
            CURLOPT_TIMEOUT => 20,
            CURLOPT_HTTPHEADER => $headers,
            CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
        ]);
        $body = curl_exec($curl);
        $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
        curl_close($curl);
    } else {
        $context = stream_context_create(['http' => [
            'method' => 'GET',
            'timeout' => 20,
            'ignore_errors' => true,
            'header' => implode("\r\n", $headers) . "\r\n",
        ]]);
        $body = @file_get_contents($url, false, $context);
        foreach ($http_response_header ?? [] as $line) {
            if (preg_match('/^HTTP\/\S+\s+(\d{3})/', $line, $match) === 1) {
                $status = (int) $match[1];
            }
        }
    }

    if ($allowMissing && $status === 404) {
        return null;
    }
    if (!is_string($body) || $status < 200 || $status >= 300) {
        throw new RuntimeException('A download-statistics provider is unavailable.');
    }
    $decoded = json_decode($body, true, 512, JSON_THROW_ON_ERROR);
    if (!is_array($decoded)) {
        throw new RuntimeException('A download-statistics provider returned invalid data.');
    }
    return $decoded;
}

function wize_cached_metric(string $key, callable $calculate): int
{
    $state = wize_read_state();
    $cached = $state['cache'][$key] ?? null;
    if (is_array($cached) && is_int($cached['value'] ?? null) &&
        is_int($cached['updatedAt'] ?? null) && time() - $cached['updatedAt'] < WIZE_BADGE_CACHE_SECONDS) {
        return $cached['value'];
    }

    try {
        $value = $calculate();
        if (!is_int($value) || $value < 0) {
            throw new RuntimeException('Invalid calculated download total.');
        }
        wize_update_state(static function (array &$current) use ($key, $value): void {
            $current['cache'][$key] = ['value' => $value, 'updatedAt' => time()];
        });
        return $value;
    } catch (Throwable $error) {
        if (is_array($cached) && is_int($cached['value'] ?? null)) {
            return $cached['value'];
        }
        throw $error;
    }
}

function wize_izzy_total(): int
{
    return wize_cached_metric('izzy', static function (): int {
        $total = 0;
        $found = false;
        for ($year = 2024; $year <= (int) gmdate('Y'); $year++) {
            $stats = wize_http_json(WIZE_IZZY_YEARLY_BASE . $year . '.json', true);
            if ($stats === null) {
                continue;
            }
            $found = true;
            $value = $stats[WIZE_PACKAGE_NAME] ?? 0;
            if (!is_int($value) || $value < 0) {
                throw new RuntimeException('IzzyOnDroid returned an invalid download total.');
            }
            $total += $value;
        }
        if (!$found) {
            throw new RuntimeException('IzzyOnDroid statistics are unavailable.');
        }
        return $total;
    });
}

function wize_github_assets(string $repository): array
{
    $assets = [];
    for ($page = 1; $page <= 20; $page++) {
        $releases = wize_http_json(WIZE_GITHUB_API_BASE . $repository . '/releases?per_page=100&page=' . $page);
        if ($releases === null) {
            break;
        }
        foreach ($releases as $release) {
            if (!is_array($release)) {
                continue;
            }
            foreach ($release['assets'] ?? [] as $asset) {
                if (!is_array($asset) || !is_int($asset['id'] ?? null) ||
                    !is_string($asset['name'] ?? null) || !str_ends_with(strtolower($asset['name']), '.apk') ||
                    !is_int($asset['download_count'] ?? null) || $asset['download_count'] < 0) {
                    continue;
                }
                $id = (string) $asset['id'];
                $assets[$id] = max($assets[$id] ?? 0, $asset['download_count']);
            }
        }
        if (count($releases) < 100) {
            break;
        }
    }
    return $assets;
}

function wize_archived_nightly_assets(): array
{
    $archive = wize_http_json(WIZE_NIGHTLY_ARCHIVE_URL, true);
    if ($archive === null || !is_array($archive['assets'] ?? null)) {
        return [];
    }
    $assets = [];
    foreach ($archive['assets'] as $id => $asset) {
        if (!is_array($asset) || !is_int($asset['downloads'] ?? null) || $asset['downloads'] < 0) {
            continue;
        }
        $assets[(string) $id] = $asset['downloads'];
    }
    return $assets;
}

function wize_github_total(): int
{
    return wize_cached_metric('github', static function (): int {
        $stable = wize_github_assets('wizdom13/WizeStream');
        $nightly = wize_archived_nightly_assets();
        foreach (wize_github_assets('wizdom13/WizeStream_Nightly') as $id => $count) {
            $nightly[$id] = max($nightly[$id] ?? 0, $count);
        }
        return array_sum($stable) + array_sum($nightly);
    });
}

function wize_website_totals(): array
{
    $website = wize_read_state()['website'];
    $arm = max(0, (int) ($website['arm'] ?? 0));
    $x86 = max(0, (int) ($website['x86_64'] ?? 0));
    return ['arm' => $arm, 'x86_64' => $x86, 'total' => $arm + $x86];
}

function wize_increment_website(string $architecture): int
{
    if (!in_array($architecture, ['arm', 'x86_64'], true)) {
        throw new InvalidArgumentException('Unknown architecture.');
    }
    return wize_update_state(static function (array &$state) use ($architecture): int {
        $state['website'][$architecture] = max(0, (int) ($state['website'][$architecture] ?? 0)) + 1;
        return $state['website'][$architecture];
    });
}

function wize_download_summary(): array
{
    $izzy = wize_izzy_total();
    $github = wize_github_total();
    $website = wize_website_totals()['total'];
    return [
        'izzy' => $izzy,
        'github' => $github,
        'website' => $website,
        'total' => $izzy + $github + $website,
    ];
}

function wize_json_headers(string $cacheControl = 'public, max-age=300'): void
{
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: ' . $cacheControl);
    header('Referrer-Policy: no-referrer');
    header('X-Content-Type-Options: nosniff');
    header("Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; sandbox");
}

function wize_send_badge(
    string $label,
    int $value,
    string $color,
    int $cacheSeconds = WIZE_BADGE_CACHE_SECONDS
): never {
    wize_json_headers('public, max-age=' . $cacheSeconds);
    echo json_encode([
        'schemaVersion' => 1,
        'label' => $label,
        'message' => number_format($value),
        'color' => $color,
        'cacheSeconds' => $cacheSeconds,
    ], JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    exit;
}

function wize_badge(
    callable $value,
    string $label,
    string $color,
    int $cacheSeconds = WIZE_BADGE_CACHE_SECONDS
): never {
    try {
        wize_send_badge($label, $value(), $color, $cacheSeconds);
    } catch (Throwable) {
        wize_json_headers('no-store');
        echo json_encode([
            'schemaVersion' => 1,
            'label' => $label,
            'message' => 'temporarily unavailable',
            'color' => 'lightgrey',
            'cacheSeconds' => 300,
        ], JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
        exit;
    }
}
