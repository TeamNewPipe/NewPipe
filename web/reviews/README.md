# WizeStream community reviews

This optional PHP/MySQL review system requires HTTPS and an external private `reviews-config.php`. Never publish real database credentials or moderator secrets; `_private/config.example.php` is only a template.

## Fresh installation

Import `_private/schema.sql` through phpMyAdmin. Configure the private PHP file outside the document root and upload the complete `web/reviews` directory, including `.htaccess` files, generated `index.php`, JavaScript and CSS.

## Upgrading a running v1 database

**Back up the MySQL database first**, then apply `_private/migrations/001_developer_replies.sql` using phpMyAdmin **before** deploying the PHP requiring schema v2. Existing review records, ratings, approval status and token hashes are not altered. Apply `_private/migrations/002_pending_review_edits.sql` after the first migration to upgrade schema v2 → v3 before deploying author-edit code. Published review edits are stored separately and require moderation. The original published rating/text and public aggregate remain unchanged until approved. Pending reviews can be edited in place; authors may cancel a pending update without deleting their review. Apply subsequent migrations in order.

## Moderation

Unlock `?view=moderate` with the existing moderator key. Publish, update or remove one official developer reply per published review. Replies cannot change the reviewer's rating or content. The public API exposes no management token, email, IP or device ID, and the client renders feedback as text rather than HTML.

Run the review tests with the disposable MySQL database in `.github/workflows/reviews.yml`. Run `python3 web/build.py` and `python3 web/check.py` before deployment. A PR does not deploy the site.

## Optional remembered review access

**Remember my review** is unchecked by default. Only an explicit opt-in stores the review's ID, private 43-character management key and local save date in that browser's localStorage. The My Reviews page uses those browser-local records only; it cannot list or recover arbitrary anonymous reviews from the server. A private link can also be remembered later from its management page. The credential never appears in the public API, local nickname lists, cookies, analytics, or third-party scripts. A browser reset or a new device cannot recover keys that were not saved elsewhere.

Anyone using the same browser profile may edit or delete a remembered review. Do not enable it on shared devices. "Forget" clears local access but does **not** delete the review; deleting an owned review removes its remembered key. When storage is disabled or full, users must save the original private link manually.
