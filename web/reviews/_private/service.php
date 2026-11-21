<?php
// SPDX-License-Identifier: GPL-3.0-or-later
declare(strict_types=1);
if (!defined('WIZE_REVIEWS_ENTRY')) { http_response_code(404); exit; }

final class ReviewHttpError extends RuntimeException
{
    public function __construct(public int $status, string $message) { parent::__construct($message); }
}

/** No framework, sessions, cookies, mailer, analytics or external requests. */
final class ReviewService
{
    private const HOUR = 3600000;
    private const PAGE_SIZE = 12;
    private Closure $clock;
    private string $origin;

    public function __construct(private PDO $db, private array $config, ?callable $clock = null)
    {
        self::validateConfig($config);
        $url = parse_url($config['public_url']);
        $this->origin = $url['scheme'] . '://' . $url['host'] . (isset($url['port']) ? ':' . $url['port'] : '');
        $this->clock = $clock ? Closure::fromCallable($clock) : static fn(): int => (int) floor(microtime(true) * 1000);
        $db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
        $db->setAttribute(PDO::ATTR_EMULATE_PREPARES, false);
        $db->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
        if ((int) $db->query('SELECT schema_version FROM wize_review_guard WHERE id = 1')->fetchColumn() !== 2) {
            throw new RuntimeException('Import the supported schema before enabling the service.');
        }
    }

    public static function validateConfig(array $config): void
    {
        $url = parse_url($config['public_url'] ?? '');
        $local = $url && in_array($url['host'] ?? '', ['localhost', '127.0.0.1', '[::1]'], true);
        if (!$url || !isset($url['scheme'], $url['host']) || isset($url['user']) || isset($url['pass']) ||
            isset($url['query']) || isset($url['fragment']) ||
            ($url['scheme'] !== 'https' && !($local && $url['scheme'] === 'http')) ||
            !preg_match('~^/(?:[A-Za-z0-9_-]+/)*$~D', $url['path'] ?? '/')) {
            throw new RuntimeException('Configure an HTTPS public_url ending in /; HTTP is for loopback testing only.');
        }
        foreach (['moderator_key', 'challenge_key', 'rate_key'] as $key) {
            if (!is_string($config[$key] ?? null) || !preg_match('/^[A-Za-z0-9_-]{43,128}$/D', $config[$key]) ||
                str_starts_with($config[$key], 'REPLACE_')) {
                throw new RuntimeException('Configure three different random URL-safe secrets of at least 32 bytes.');
            }
        }
        if (count(array_unique([$config['moderator_key'], $config['challenge_key'], $config['rate_key']])) !== 3) {
            throw new RuntimeException('Use a separate secret for each purpose.');
        }
    }

    private function now(): int { return ($this->clock)(); }
    private function query(string $sql, array $params = []): PDOStatement
    {
        $statement = $this->db->prepare($sql);
        foreach (array_values($params) as $index => $value) {
            $statement->bindValue($index + 1, $value, is_int($value) ? PDO::PARAM_INT : PDO::PARAM_STR);
        }
        $statement->execute();
        return $statement;
    }
    private function transaction(callable $action): mixed
    {
        $this->db->beginTransaction();
        try {
            // Serializes bounded-queue admission and limiter capacity across PHP workers.
            if (!$this->query('SELECT id FROM wize_review_guard WHERE id = 1 FOR UPDATE')->fetch()) {
                throw new RuntimeException('Import the schema before enabling the service.');
            }
            $result = $action();
            $this->db->commit();
            return $result;
        } catch (Throwable $error) {
            if ($this->db->inTransaction()) { $this->db->rollBack(); }
            throw $error;
        }
    }

    public function maintenance(): void
    {
        $now = $this->now();
        $this->query('DELETE FROM wize_review_challenges WHERE expires_at <= ?', [$now]);
        $this->query('DELETE FROM wize_review_limits WHERE expires_at <= ?', [$now]);
        $this->query("DELETE FROM wize_reviews WHERE state = 'pending' AND submitted_at <= ?", [$now - 30 * 24 * self::HOUR]);
    }

    private function limit(string $ip, string $kind, int $maximum): void
    {
        $period = intdiv($this->now(), self::HOUR);
        $hourKey = hash_hmac('sha256', 'hour:' . $period, $this->config['rate_key']);
        $key = hash_hmac('sha256', $kind . ':' . $ip, $hourKey);
        $expires = ($period + 1) * self::HOUR;
        $this->transaction(function () use ($key, $expires, $maximum): void {
            $record = $this->query('SELECT attempts FROM wize_review_limits WHERE bucket_key = ?', [$key])->fetch();
            if (!$record && (int) $this->query('SELECT COUNT(*) FROM wize_review_limits')->fetchColumn() >= (int) ($this->config['rate_capacity'] ?? 10000)) {
                throw new ReviewHttpError(429, 'Please try again later.');
            }
            if ($record && (int) $record['attempts'] >= $maximum) {
                throw new ReviewHttpError(429, 'Please try again later.');
            }
            $this->query('INSERT INTO wize_review_limits (bucket_key, attempts, expires_at) VALUES (?, 1, ?) ON DUPLICATE KEY UPDATE attempts = attempts + 1', [$key, $expires]);
        });
    }

    public function headers(string $type = 'application/json; charset=utf-8'): array
    {
        return [
            'Content-Type' => $type, 'Cache-Control' => 'no-store',
            'Content-Security-Policy' => "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
            'Referrer-Policy' => 'no-referrer', 'X-Content-Type-Options' => 'nosniff',
            'X-Frame-Options' => 'DENY', 'Permissions-Policy' => 'camera=(), microphone=(), geolocation=()',
        ];
    }
    private function json(array $data, int $status = 200): array
    {
        return ['status' => $status, 'headers' => $this->headers(), 'body' => json_encode($data, JSON_THROW_ON_ERROR | JSON_UNESCAPED_UNICODE)];
    }
    private function body(array $headers, string $raw): array
    {
        if (($headers['origin'] ?? '') !== $this->origin || ($headers['sec-fetch-site'] ?? '') === 'cross-site') {
            throw new ReviewHttpError(403, 'Open this form on the review website.');
        }
        if (strtolower(trim(explode(';', $headers['content-type'] ?? '')[0])) !== 'application/json' || !empty($headers['content-encoding'])) {
            throw new ReviewHttpError(415, 'Send uncompressed JSON.');
        }
        if (strlen($raw) > 16384 || (int) ($headers['content-length'] ?? 0) > 16384) {
            throw new ReviewHttpError(413, 'Review is too large.');
        }
        try { $object = json_decode($raw, false, 32, JSON_THROW_ON_ERROR); }
        catch (JsonException) { throw new ReviewHttpError(400, 'Invalid JSON.'); }
        if (!is_object($object)) { throw new ReviewHttpError(400, 'Invalid JSON.'); }
        return get_object_vars($object);
    }
    private function text(mixed $value, int $maximum, bool $optional = false): string
    {
        if ($optional && $value === null) { return ''; }
        if (!is_string($value) || !preg_match('//u', $value)) { throw new ReviewHttpError(400, 'Please check the review fields.'); }
        $value = preg_replace('/^\s+|\s+$/u', '', $value);
        if (preg_match_all('/./us', $value) > $maximum || preg_match('/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/', $value)) {
            throw new ReviewHttpError(400, 'Please check the review fields.');
        }
        return $value;
    }
    private function validId(mixed $value): bool
    {
        return is_string($value) && (bool) preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/D', $value);
    }
    private function challenge(mixed $value): array
    {
        if (!is_string($value) || strlen($value) > 160 || !preg_match('/^(\d{10,15})\.([0-9a-f]{32})\.([0-9a-f]{64})$/D', $value, $match) ||
            !hash_equals(hash_hmac('sha256', $match[1] . '.' . $match[2], $this->config['challenge_key']), $match[3])) {
            throw new ReviewHttpError(400, 'Reload the form and try again.');
        }
        $age = $this->now() - (int) $match[1];
        if ($age < 2000 || $age >= 1200000) {
            throw new ReviewHttpError(400, 'Take a moment to review your text, or reload an expired form.');
        }
        return [hash('sha256', $value), (int) $match[1] + 1200000];
    }
    private function moderator(array $headers, string $ip): void
    {
        $key = $headers['x-moderator-key'] ?? '';
        if (strlen($key) > 128 || !hash_equals(hash('sha256', $this->config['moderator_key']), hash('sha256', $key))) {
            $this->limit($ip, 'moderator-failed', 30);
            throw new ReviewHttpError(401, 'Moderator key required.');
        }
    }
    private function owned(array $headers): array
    {
        if (!preg_match('/^Review ([0-9a-f-]{36}):([A-Za-z0-9_-]{43})$/D', $headers['x-review-authorization'] ?? '', $match) || !$this->validId($match[1])) {
            throw new ReviewHttpError(404, 'Review unavailable.');
        }
        $record = $this->query('SELECT * FROM wize_reviews WHERE id = ?', [$match[1]])->fetch();
        if (!$record || !hash_equals($record['delete_hash'], hash('sha256', $match[2]))) { throw new ReviewHttpError(404, 'Review unavailable.'); }
        return $record;
    }
    private function page(array $query): int
    {
        $value = $query['page'] ?? '1';
        if (!is_string($value) || !preg_match('/^[1-9][0-9]{0,4}$/D', $value) || (int) $value > 10000) { throw new ReviewHttpError(400, 'Invalid page.'); }
        return (int) $value;
    }
    private function records(array $rows): array
    {
        return array_map(static function (array $row): array {
            $row['rating'] = (int) $row['rating'];
            return $row;
        }, $rows);
    }
    public function summary(): array
    {
        $aggregate = $this->query("SELECT COUNT(*) AS total, AVG(rating) AS average FROM wize_reviews WHERE state = 'published'")->fetch();
        $distribution = [0, 0, 0, 0, 0];
        foreach ($this->query("SELECT rating, COUNT(*) AS total FROM wize_reviews WHERE state = 'published' GROUP BY rating")->fetchAll() as $row) {
            $distribution[(int) $row['rating'] - 1] = (int) $row['total'];
        }
        return ['count' => (int) $aggregate['total'], 'average' => $aggregate['total'] ? round((float) $aggregate['average'], 2) : null, 'distribution' => $distribution];
    }

    /** Used by both HTTP entry points and integration tests; secrets never enter a URL. */
    public function handle(string $method, string $action, array $query = [], array $headers = [], string $raw = '', string $ip = 'unknown'): array
    {
        $headers = array_change_key_case($headers, CASE_LOWER);
        try {
            $this->maintenance();
            if ($method === 'GET' && $action === 'summary') { return $this->json($this->summary()); }
            if ($method === 'GET' && in_array($action, ['badge-svg', 'badge-json'], true)) {
                $data = $this->summary();
                $message = $data['count'] ? number_format($data['average'], 1, '.', '') . '/5 · ' . $data['count'] . ($data['count'] === 1 ? ' review' : ' reviews') : 'No reviews yet';
                if ($action === 'badge-json') { return $this->json(['schemaVersion' => 1, 'label' => 'Community reviews', 'message' => $message, 'color' => $data['count'] ? 'blue' : 'lightgrey']); }
                $safe = htmlspecialchars($message, ENT_QUOTES | ENT_XML1, 'UTF-8');
                $right = max(106, strlen($message) * 7 + 20); $width = 124 + $right; $center = 124 + $right / 2;
                $svg = '<svg xmlns="http://www.w3.org/2000/svg" width="' . $width . '" height="24" role="img" aria-label="Community reviews: ' . $safe . '"><title>Community reviews: ' . $safe . '</title><rect width="124" height="24" fill="#555"/><rect x="124" width="' . $right . '" height="24" fill="#b62431"/><g fill="#fff" font-family="Verdana,Arial,sans-serif" font-size="11" text-anchor="middle"><text x="62" y="16">Community reviews</text><text x="' . $center . '" y="16">' . $safe . '</text></g></svg>';
                return ['status' => 200, 'headers' => $this->headers('image/svg+xml; charset=utf-8'), 'body' => $svg];
            }
            if ($method === 'GET' && $action === 'reviews') {
                $page = $this->page($query);
                $reviews = $this->records($this->query("SELECT r.id, r.rating, r.nickname, r.content, r.app_version AS appVersion, r.created_day AS date, rep.content AS developerReply, rep.updated_day AS replyDate FROM wize_reviews AS r LEFT JOIN wize_review_replies AS rep ON rep.review_id = r.id WHERE r.state = 'published' ORDER BY r.submitted_at DESC, r.id DESC LIMIT ? OFFSET ?", [self::PAGE_SIZE, ($page - 1) * self::PAGE_SIZE])->fetchAll());
                return $this->json(['reviews' => $reviews, 'page' => $page, 'pageSize' => self::PAGE_SIZE] + $this->summary());
            }
            if ($method === 'GET' && $action === 'challenge') {
                if (($headers['sec-fetch-site'] ?? '') === 'cross-site') { throw new ReviewHttpError(403, 'Open the form on the review website.'); }
                $this->limit($ip, 'challenge', 40);
                $payload = $this->now() . '.' . bin2hex(random_bytes(16));
                return $this->json(['challenge' => $payload . '.' . hash_hmac('sha256', $payload, $this->config['challenge_key']), 'minimumWaitMs' => 2000]);
            }
            if ($method === 'POST' && $action === 'reviews') {
                $input = $this->body($headers, $raw);
                if (!$this->validId($input['id'] ?? null) || !is_string($input['deletionToken'] ?? null) || !preg_match('/^[A-Za-z0-9_-]{43}$/D', $input['deletionToken'])) {
                    throw new ReviewHttpError(400, 'Reload the form and try again.');
                }
                $existing = $this->query('SELECT delete_hash, state FROM wize_reviews WHERE id = ?', [$input['id']])->fetch();
                if ($existing) {
                    $this->limit($ip, 'manage', 120);
                    if (!hash_equals($existing['delete_hash'], hash('sha256', $input['deletionToken']))) { throw new ReviewHttpError(409, 'Review could not be accepted.'); }
                    return $this->json(['id' => $input['id'], 'state' => $existing['state'], 'alreadyReceived' => true]);
                }
                $this->limit($ip, 'submission', (int) ($this->config['per_hour_limit'] ?? 5));
                if (!empty($input['website'])) { throw new ReviewHttpError(400, 'Review could not be accepted.'); }
                [$digest, $expiry] = $this->challenge($input['challenge'] ?? null);
                $content = $this->text($input['content'] ?? null, 3000);
                $nickname = $this->text($input['nickname'] ?? null, 40, true);
                $version = $this->text($input['appVersion'] ?? null, 32, true);
                if (!is_int($input['rating'] ?? null) || $input['rating'] < 1 || $input['rating'] > 5 || preg_match_all('/./us', $content) < 10 || ($input['consent'] ?? false) !== true) {
                    throw new ReviewHttpError(400, 'Choose 1–5 stars, write at least 10 characters, and agree to publication.');
                }
                return $this->transaction(function () use ($input, $digest, $expiry, $content, $nickname, $version): array {
                    // Recheck after acquiring the shared lock: concurrent retries create one row.
                    $existing = $this->query('SELECT delete_hash, state FROM wize_reviews WHERE id = ?', [$input['id']])->fetch();
                    if ($existing) {
                        if (!hash_equals($existing['delete_hash'], hash('sha256', $input['deletionToken']))) { throw new ReviewHttpError(409, 'Review could not be accepted.'); }
                        return $this->json(['id' => $input['id'], 'state' => $existing['state'], 'alreadyReceived' => true]);
                    }
                    if ((int) $this->query("SELECT COUNT(*) FROM wize_reviews WHERE state = 'pending'")->fetchColumn() >= (int) ($this->config['max_pending'] ?? 500)) {
                        throw new ReviewHttpError(503, 'The review queue is full. Please try again later.');
                    }
                    if ($this->query('SELECT digest FROM wize_review_challenges WHERE digest = ?', [$digest])->fetch()) { throw new ReviewHttpError(409, 'This form was already submitted. Reload before writing another review.'); }
                    $this->query('INSERT INTO wize_review_challenges (digest, expires_at) VALUES (?, ?)', [$digest, $expiry]);
                    $this->query("INSERT INTO wize_reviews (id, rating, nickname, content, app_version, delete_hash, state, created_day, submitted_at) VALUES (?, ?, ?, ?, ?, ?, 'pending', ?, ?)", [$input['id'], $input['rating'], $nickname, $content, $version, hash('sha256', $input['deletionToken']), gmdate('Y-m-d', intdiv($this->now(), 1000)), $this->now()]);
                    return $this->json(['id' => $input['id'], 'state' => 'pending'], 201);
                });
            }
            if ($method === 'POST' && $action === 'manage') {
                $input = $this->body($headers, $raw);
                $this->limit($ip, 'manage', 120);
                $record = $this->owned($headers);
                if (($input['operation'] ?? 'read') === 'delete') {
                    $this->query('DELETE FROM wize_reviews WHERE id = ?', [$record['id']]);
                    return $this->json(['deleted' => true]);
                }
                if (($input['operation'] ?? 'read') !== 'read') { throw new ReviewHttpError(400, 'Invalid management action.'); }
                return $this->json(['rating' => (int) $record['rating'], 'nickname' => $record['nickname'], 'content' => $record['content'], 'appVersion' => $record['app_version'], 'date' => $record['created_day'], 'state' => $record['state']]);
            }
            if (in_array($method, ['GET', 'POST'], true) && $action === 'moderation') {
                $this->moderator($headers, $ip);
                if ($method === 'GET') {
                    $state = $query['state'] ?? 'pending'; $page = $this->page($query);
                    if (!in_array($state, ['pending', 'published'], true)) { throw new ReviewHttpError(400, 'Invalid filter.'); }
                    $count = (int) $this->query('SELECT COUNT(*) FROM wize_reviews WHERE state = ?', [$state])->fetchColumn();
                    $reviews = $this->records($this->query('SELECT r.id, r.rating, r.nickname, r.content, r.app_version AS appVersion, r.created_day AS date, r.state, rep.content AS developerReply, rep.updated_day AS replyDate FROM wize_reviews AS r LEFT JOIN wize_review_replies AS rep ON rep.review_id = r.id WHERE r.state = ? ORDER BY r.submitted_at ASC, r.id ASC LIMIT ? OFFSET ?', [$state, self::PAGE_SIZE, ($page - 1) * self::PAGE_SIZE])->fetchAll());
                    return $this->json(['reviews' => $reviews, 'count' => $count, 'page' => $page, 'pageSize' => self::PAGE_SIZE]);
                }
                $input = $this->body($headers, $raw);
                if (in_array($input['action'] ?? null, ['reply', 'remove-reply'], true)) {
                    if (!$this->validId($input['id'] ?? null)) { throw new ReviewHttpError(400, 'Invalid review.'); }
                    $this->limit($ip, 'moderator-write', 120);
                    return $this->transaction(function () use ($input): array {
                        $record = $this->query('SELECT state FROM wize_reviews WHERE id = ?', [$input['id']])->fetch();
                        if (!$record || $record['state'] !== 'published') { throw new ReviewHttpError(404, 'Published review unavailable.'); }
                        if ($input['action'] === 'remove-reply') {
                            $this->query('DELETE FROM wize_review_replies WHERE review_id = ?', [$input['id']]);
                            return $this->json(['success' => true]);
                        }
                        $content = $this->text($input['content'] ?? null, 1500);
                        if ($content === '') { throw new ReviewHttpError(400, 'Write a reply before saving.'); }
                        $day = gmdate('Y-m-d', intdiv($this->now(), 1000));
                        $this->query('INSERT INTO wize_review_replies (review_id, content, updated_day) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE content = VALUES(content), updated_day = VALUES(updated_day)', [$input['id'], $content, $day]);
                        return $this->json(['success' => true, 'developerReply' => $content, 'replyDate' => $day]);
                    });
                }
                if (!$this->validId($input['id'] ?? null) || !in_array($input['action'] ?? '', ['publish', 'remove'], true)) { throw new ReviewHttpError(400, 'Invalid moderation action.'); }
                if ($input['action'] === 'publish') {
                    if (!$this->query('SELECT id FROM wize_reviews WHERE id = ?', [$input['id']])->fetch()) { throw new ReviewHttpError(404, 'Review unavailable.'); }
                    $this->query("UPDATE wize_reviews SET state = 'published' WHERE id = ?", [$input['id']]);
                } elseif (!$this->query('DELETE FROM wize_reviews WHERE id = ?', [$input['id']])->rowCount()) { throw new ReviewHttpError(404, 'Review unavailable.'); }
                return $this->json(['success' => true]);
            }
            throw new ReviewHttpError(404, 'Page not found.');
        } catch (ReviewHttpError $error) {
            return $this->json(['error' => $error->getMessage()], $error->status);
        } catch (Throwable) {
            // Never log URLs, headers, credentials, review text or SQL exceptions.
            return $this->json(['error' => 'The request could not be completed. Please try again.'], 503);
        }
    }
}
