<?php
// SPDX-License-Identifier: GPL-3.0-or-later
declare(strict_types=1);
if (PHP_SAPI !== 'cli') { http_response_code(404); exit; }
define('WIZE_REVIEWS_ENTRY', true);
require dirname(__DIR__) . '/service.php';

function testDatabase(): PDO
{
    $dsn = getenv('REVIEWS_TEST_DSN') ?: '';
    if (!preg_match('/;dbname=wizestream_reviews_test(?:;|$)/', $dsn)) {
        throw new RuntimeException('Use only the disposable wizestream_reviews_test database.');
    }
    return new PDO($dsn, getenv('REVIEWS_TEST_USER') ?: '', getenv('REVIEWS_TEST_PASSWORD') ?: '', [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_EMULATE_PREPARES => false,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
}
function testConfig(): array
{
    return ['public_url' => 'https://wizestream.org/reviews/', 'moderator_key' => str_repeat('a', 43),
        'challenge_key' => str_repeat('b', 43), 'rate_key' => str_repeat('c', 43), 'per_hour_limit' => 100];
}
function equal(mixed $actual, mixed $expected, string $context = ''): void
{
    if ($actual !== $expected) { throw new RuntimeException($context . ' expected ' . var_export($expected, true) . ', got ' . var_export($actual, true)); }
}
function payload(array $response): array { return json_decode($response['body'], true, 32, JSON_THROW_ON_ERROR); }
function uuid(): string
{
    $bytes = random_bytes(16); $bytes[6] = chr((ord($bytes[6]) & 0x0f) | 0x40); $bytes[8] = chr((ord($bytes[8]) & 0x3f) | 0x80);
    $hex = bin2hex($bytes);
    return substr($hex, 0, 8) . '-' . substr($hex, 8, 4) . '-' . substr($hex, 12, 4) . '-' . substr($hex, 16, 4) . '-' . substr($hex, 20);
}
function draft(ReviewService $service, int &$clock, array $overrides = []): array
{
    $challenge = payload($service->handle('GET', 'challenge'))['challenge'];
    $clock += 3000;
    return array_replace(['id' => uuid(), 'deletionToken' => rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '='),
        'challenge' => $challenge, 'rating' => 5, 'content' => 'A useful and thoughtful streaming app.', 'nickname' => '',
        'appVersion' => '', 'consent' => true, 'website' => ''], $overrides);
}
function post(ReviewService $service, string $action, array $data, array $headers = [], string $ip = '192.0.2.1'): array
{
    return $service->handle('POST', $action, [], array_replace(['origin' => 'https://wizestream.org', 'content-type' => 'application/json'], $headers), json_encode((object) $data, JSON_THROW_ON_ERROR), $ip);
}
function submit(ReviewService $service, array $data): array { return post($service, 'reviews', $data); }
function moderate(ReviewService $service, string $id, string $action = 'publish'): array
{
    return post($service, 'moderation', ['id' => $id, 'action' => $action], ['x-moderator-key' => testConfig()['moderator_key']]);
}
function manage(ReviewService $service, array $review, string $operation = 'read'): array
{
    return post($service, 'manage', ['operation' => $operation], ['x-review-authorization' => 'Review ' . $review['id'] . ':' . $review['deletionToken']]);
}
function fixture(array $options = []): array
{
    $db = testDatabase();
    foreach (['wize_review_edits', 'wize_review_replies', 'wize_reviews', 'wize_review_challenges', 'wize_review_limits'] as $table) { $db->exec('DELETE FROM ' . $table); }
    $clock = new stdClass(); $clock->now = 1794571200000;
    $config = array_replace(testConfig(), $options);
    $service = new ReviewService($db, $config, static fn(): int => $clock->now);
    return [$service, $clock, $db, $config];
}

// A second PHP process can use the same DB/secrets: no in-memory challenges or limiters.
if (($argv[1] ?? '') === 'request') {
    $input = json_decode(stream_get_contents(STDIN), true, 32, JSON_THROW_ON_ERROR);
    $service = new ReviewService(testDatabase(), $input['config'], static fn(): int => $input['clock']);
    echo json_encode(submit($service, $input['review']), JSON_THROW_ON_ERROR);
    exit;
}

$db = testDatabase();
$db->exec(file_get_contents(dirname(__DIR__) . '/schema.sql'));
$tests = [];
$tests['only moderated reviews count; all star ratings count equally'] = function (): void {
    [$s, $clock] = fixture();
    $low = draft($s, $clock->now, ['rating' => 1, 'content' => 'The playback controls need more work.']);
    $high = draft($s, $clock->now);
    equal(submit($s, $low)['status'], 201); equal(submit($s, $high)['status'], 201);
    equal(payload($s->handle('GET', 'summary')), ['count' => 0, 'average' => null, 'distribution' => [0, 0, 0, 0, 0]]);
    equal(payload($s->handle('GET', 'reviews'))['reviews'], []);
    equal(moderate($s, $low['id'])['status'], 200); equal(moderate($s, $high['id'])['status'], 200);
    $summary = payload($s->handle('GET', 'summary'));
    equal($summary['count'], 2); equal((float) $summary['average'], 3.0); equal($summary['distribution'], [1, 0, 0, 0, 1]);
    $public = $s->handle('GET', 'reviews')['body'];
    foreach ([$low['deletionToken'], 'delete_hash', 'submitted_at', '192.0.2.1', testConfig()['moderator_key']] as $secret) { equal(str_contains($public, $secret), false, 'public secret'); }
};
$tests['moderation is authorized on the server for both reads and writes'] = function (): void {
    [$s, $clock] = fixture(); $r = draft($s, $clock->now); submit($s, $r);
    equal($s->handle('GET', 'moderation')['status'], 401);
    equal(post($s, 'moderation', ['id' => $r['id'], 'action' => 'publish'])['status'], 401);
    equal($s->handle('GET', 'moderation', [], ['x-moderator-key' => 'wrong'])['status'], 401);
    equal($s->handle('GET', 'moderation', [], ['x-moderator-key' => testConfig()['moderator_key']])['status'], 200);
    equal(post($s, 'moderation', ['id' => $r['id'], 'action' => 'publish'], ['x-moderator-key' => testConfig()['moderator_key'], 'origin' => 'https://evil.example'])['status'], 403);
};
$tests['developer replies require moderation and preserve rating'] = function (): void {
    [$s, $clock, $db] = fixture();
    $review = draft($s, $clock->now, ['rating' => 2]);
    equal(submit($s, $review)['status'], 201);
    $headers = ['x-moderator-key' => testConfig()['moderator_key']];
    $reply = ['id' => $review['id'], 'action' => 'reply', 'content' => 'We are fixing this.'];
    equal(post($s, 'moderation', $reply)['status'], 401);
    equal(post($s, 'moderation', $reply, $headers)['status'], 404);
    equal(moderate($s, $review['id'])['status'], 200);
    equal(post($s, 'moderation', array_replace($reply, ['content' => '']), $headers)['status'], 400);
    equal(post($s, 'moderation', $reply, $headers)['status'], 200);
    equal(payload($s->handle('GET', 'reviews'))['reviews'][0]['developerReply'], $reply['content']);
    equal(payload($s->handle('GET', 'summary'))['distribution'], [0, 1, 0, 0, 0]);
    equal(post($s, 'moderation', array_replace($reply, ['content' => '<script>test</script>']), $headers)['status'], 200);
    equal(payload($s->handle('GET', 'moderation', ['state' => 'published'], $headers))['reviews'][0]['developerReply'], '<script>test</script>');
    equal(post($s, 'moderation', ['id' => $review['id'], 'action' => 'remove-reply'], $headers)['status'], 200);
    equal(payload($s->handle('GET', 'reviews'))['reviews'][0]['developerReply'], null);
    equal(post($s, 'moderation', $reply, $headers)['status'], 200);
    equal(manage($s, $review, 'delete')['status'], 200);
    equal((int) $db->query('SELECT COUNT(*) FROM wize_review_replies')->fetchColumn(), 0);
};
$tests['review author edits preserve published rating until moderator approval'] = function (): void {
    [$s, $clock, $db] = fixture();
    $review = draft($s, $clock->now, ['content' => 'Original review feedback.', 'rating' => 5]);
    equal(submit($s, $review)['status'], 201);
    $author = ['x-review-authorization' => 'Review ' . $review['id'] . ':' . $review['deletionToken']];
    $changes = ['operation' => 'edit', 'rating' => 2, 'nickname' => 'Updated', 'content' => 'The issue remains in this version.', 'appVersion' => '1.43.0'];
    equal(post($s, 'manage', $changes, ['x-review-authorization' => 'Review ' . $review['id'] . ':' . str_repeat('a', 43)])['status'], 404);
    equal(post($s, 'manage', $changes, $author)['status'], 200); // pending edit replaces unpublished content
    equal(payload(manage($s, $review))['rating'], 2);
    equal(moderate($s, $review['id'])['status'], 200);
    $edited = array_replace($changes, ['rating' => 3, 'content' => 'The landscape UI needs more compact controls.']);
    equal(post($s, 'manage', $edited, $author)['status'], 202);
    equal(payload($s->handle('GET', 'reviews'))['reviews'][0]['rating'], 2);
    equal(payload($s->handle('GET', 'summary'))['distribution'], [0, 1, 0, 0, 0]);
    equal(payload(manage($s, $review))['pendingEdit']['rating'], 3);
    $moderator = ['x-moderator-key' => testConfig()['moderator_key']];
    $queue = payload($s->handle('GET', 'moderation', ['state' => 'edits'], $moderator));
    equal($queue['count'], 1); equal($queue['reviews'][0]['rating'], 3);
    equal(moderate($s, $review['id'], 'approve-edit')['status'], 200);
    equal(payload($s->handle('GET', 'reviews'))['reviews'][0]['rating'], 3);
    equal(payload($s->handle('GET', 'summary'))['distribution'], [0, 0, 1, 0, 0]);
    equal(post($s, 'manage', array_replace($edited, ['rating' => 1]), $author)['status'], 202);
    equal(moderate($s, $review['id'], 'reject-edit')['status'], 200);
    equal(payload($s->handle('GET', 'reviews'))['reviews'][0]['rating'], 3);
    equal(post($s, 'manage', array_replace($edited, ['rating' => 1]), $author)['status'], 202);
    equal(post($s, 'manage', ['operation' => 'cancel-edit'], $author)['status'], 200);
    equal(payload(manage($s, $review))['pendingEdit'], null);
    equal(post($s, 'manage', array_replace($edited, ['content' => 'short']), $author)['status'], 400);
    equal(post($s, 'manage', $edited, ['x-review-authorization' => $author['x-review-authorization'], 'origin' => 'https://wrong.example'])['status'], 403);
    equal(post($s, 'manage', $edited, $author)['status'], 202);
    equal(manage($s, $review, 'delete')['status'], 200);
    equal((int) $db->query('SELECT COUNT(*) FROM wize_review_edits')->fetchColumn(), 0);
};
$tests['author deletion and moderator removal purge live records and ratings'] = function (): void {
    [$s, $clock, $db] = fixture();
    foreach ([false, true] as $published) {
        $r = draft($s, $clock->now); submit($s, $r); if ($published) { moderate($s, $r['id']); }
        equal(manage($s, array_replace($r, ['deletionToken' => str_repeat('z', 43)]))['status'], 404);
        equal(manage($s, $r)['status'], 200); equal(manage($s, $r, 'delete')['status'], 200); equal(manage($s, $r)['status'], 404);
        $r = draft($s, $clock->now); submit($s, $r); if ($published) { moderate($s, $r['id']); }
        equal(moderate($s, $r['id'], 'remove')['status'], 200); equal(manage($s, $r)['status'], 404);
    }
    equal((int) $db->query('SELECT COUNT(*) FROM wize_reviews')->fetchColumn(), 0);
    equal(payload($s->handle('GET', 'summary'))['count'], 0);
};
$tests['idempotent receipt recovery survives expiry and a different PHP worker'] = function (): void {
    [$s, $clock, $db, $config] = fixture(); $r = draft($s, $clock->now);
    equal(submit($s, $r)['status'], 201); $clock->now += 25 * 60000;
    $second = new ReviewService(testDatabase(), $config, static fn(): int => $clock->now);
    $retry = submit($second, $r); equal($retry['status'], 200); equal(payload($retry)['alreadyReceived'], true);
    equal((int) $db->query('SELECT COUNT(*) FROM wize_reviews')->fetchColumn(), 1);
    equal(submit($second, array_replace($r, ['deletionToken' => str_repeat('z', 43)]))['status'], 409);
};
$tests['signed challenges have minimum/maximum age and single-use enforcement'] = function (): void {
    [$s, $clock] = fixture(); $r = draft($s, $clock->now);
    $tampered = substr($r['challenge'], 0, -1) . (str_ends_with($r['challenge'], 'a') ? 'b' : 'a');
    equal(submit($s, array_replace($r, ['challenge' => $tampered]))['status'], 400);
    equal(submit($s, $r)['status'], 201);
    equal(submit($s, array_replace($r, ['id' => uuid()]))['status'], 409);
    $old = draft($s, $clock->now); $clock->now += 1200000; equal(submit($s, $old)['status'], 400);
    $recent = draft($s, $clock->now); $clock->now -= 3000; equal(submit($s, $recent)['status'], 400);
};
$tests['validation rejects malformed data and accepts Unicode without HTML execution'] = function (): void {
    [$s, $clock] = fixture();
    foreach ([['rating' => 0], ['rating' => 6], ['rating' => 2.5], ['rating' => '5'], ['consent' => false], ['content' => 'short'], ['content' => str_repeat('x', 3001)], ['nickname' => str_repeat('x', 41)], ['appVersion' => str_repeat('x', 33)], ['website' => 'spam']] as $invalid) {
        equal(submit($s, draft($s, $clock->now, $invalid))['status'], 400, json_encode($invalid));
    }
    $r = draft($s, $clock->now, ['content' => '<script>alert(1)</script> مرحبا بالعالم 🌟', 'nickname' => 'مستخدم']);
    equal(submit($s, $r)['status'], 201); moderate($s, $r['id']);
    equal(payload($s->handle('GET', 'reviews'))['reviews'][0]['content'], $r['content']);
    $script = file_get_contents(dirname(__DIR__, 2) . '/reviews.js');
    equal(str_contains($script, 'innerHTML'), false, 'review rendering uses textContent');
};
$tests['same-origin JSON and body limits protect every write'] = function (): void {
    [$s, $clock] = fixture(); $r = draft($s, $clock->now);
    equal(post($s, 'reviews', $r, ['origin' => 'https://evil.example'])['status'], 403);
    equal(post($s, 'reviews', $r, ['sec-fetch-site' => 'cross-site'])['status'], 403);
    equal(post($s, 'reviews', $r, ['content-type' => 'text/plain'])['status'], 415);
    equal(post($s, 'reviews', $r, ['content-encoding' => 'gzip'])['status'], 415);
    equal(post($s, 'reviews', ['content' => str_repeat('x', 17000)])['status'], 413);
    equal($s->handle('POST', 'reviews', [], ['origin' => 'https://wizestream.org', 'content-type' => 'application/json'], '[]')['status'], 400);
    equal($s->handle('GET', 'reviews', ['page' => '1 OR 1=1'])['status'], 400);
    equal($s->handle('GET', 'reviews', ['page' => ['1']])['status'], 400);
};
$tests['network abuse limits are shared, short-lived and not attached to reviews'] = function (): void {
    [$s, $clock, $db, $config] = fixture(['per_hour_limit' => 2]);
    equal(submit($s, draft($s, $clock->now))['status'], 201);
    $second = new ReviewService(testDatabase(), $config, static fn(): int => $clock->now);
    equal(submit($second, draft($second, $clock->now))['status'], 201);
    equal(submit($s, draft($s, $clock->now))['status'], 429);
    $old = $db->query('SELECT bucket_key FROM wize_review_limits')->fetchAll(PDO::FETCH_COLUMN);
    $clock->now += 3600001; $s->maintenance();
    equal((int) $db->query('SELECT COUNT(*) FROM wize_review_limits')->fetchColumn(), 0);
    equal(submit($s, draft($s, $clock->now))['status'], 201);
    $new = $db->query('SELECT bucket_key FROM wize_review_limits')->fetchAll(PDO::FETCH_COLUMN);
    equal(array_intersect($old, $new), []);
    $stored = json_encode($db->query('SELECT * FROM wize_review_limits')->fetchAll());
    equal(str_contains($stored, '192.0.2.1'), false);
    $columns = $db->query('SHOW COLUMNS FROM wize_reviews')->fetchAll(PDO::FETCH_COLUMN);
    foreach (['ip', 'bucket_key', 'user_agent', 'email', 'device_id'] as $column) { equal(in_array($column, $columns), false); }
};
$tests['pending retention, pagination, badges and headers preserve privacy'] = function (): void {
    [$s, $clock] = fixture(); $pending = draft($s, $clock->now); submit($s, $pending);
    equal(payload($s->handle('GET', 'badge-json'))['message'], 'No reviews yet');
    for ($i = 0; $i < 13; $i++) { $r = draft($s, $clock->now); submit($s, $r); moderate($s, $r['id']); }
    equal(count(payload($s->handle('GET', 'reviews'))['reviews']), 12);
    equal(count(payload($s->handle('GET', 'reviews', ['page' => '2']))['reviews']), 1);
    equal(payload($s->handle('GET', 'badge-json'))['message'], '5.0/5 · 13 reviews');
    $clock->now += 31 * 24 * 3600000; $s->maintenance();
    equal(manage($s, $pending)['status'], 404); equal(payload($s->handle('GET', 'summary'))['count'], 13);
    foreach (['reviews', 'summary', 'badge-svg'] as $action) {
        $response = $s->handle('GET', $action); equal($response['headers']['Cache-Control'], 'no-store');
        equal($response['headers']['Referrer-Policy'], 'no-referrer'); equal(isset($response['headers']['Set-Cookie']), false);
    }
};
$tests['concurrent PHP workers enforce challenge reuse and queue capacity atomically'] = function (): void {
    foreach (['challenge', 'capacity', 'retry'] as $case) {
        [$s, $clock, $db, $config] = fixture($case === 'capacity' ? ['max_pending' => 1] : []);
        $a = draft($s, $clock->now); $b = $case === 'retry' ? $a : ($case === 'challenge' ? array_replace($a, ['id' => uuid()]) : draft($s, $clock->now));
        $processes = [];
        foreach ([$a, $b] as $review) {
            $pipes = [];
            $process = proc_open([PHP_BINARY, __FILE__, 'request'], [0 => ['pipe', 'r'], 1 => ['pipe', 'w'], 2 => ['pipe', 'w']], $pipes);
            if (!is_resource($process)) { throw new RuntimeException('Could not start concurrent PHP test.'); }
            fwrite($pipes[0], json_encode(['review' => $review, 'config' => $config, 'clock' => $clock->now])); fclose($pipes[0]);
            $processes[] = [$process, $pipes];
        }
        $statuses = [];
        foreach ($processes as [$process, $pipes]) {
            $output = stream_get_contents($pipes[1]); $errors = stream_get_contents($pipes[2]); fclose($pipes[1]); fclose($pipes[2]);
            equal(proc_close($process), 0, $errors); $statuses[] = json_decode($output, true, 32, JSON_THROW_ON_ERROR)['status'];
        }
        sort($statuses); equal($statuses, $case === 'retry' ? [200, 201] : ($case === 'challenge' ? [201, 409] : [201, 503]));
        equal((int) $db->query('SELECT COUNT(*) FROM wize_reviews')->fetchColumn(), 1);
    }
};

$failed = 0;
foreach ($tests as $name => $test) {
    try { $test(); echo "PASS: $name\n"; }
    catch (Throwable $error) { $failed++; fwrite(STDERR, "FAIL: $name: " . $error->getMessage() . "\n"); }
}
if ($failed) { exit(1); }
echo count($tests) . " PHP/MySQL integration checks passed.\n";
