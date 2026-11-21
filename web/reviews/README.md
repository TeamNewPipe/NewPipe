# WizeStream community reviews

This optional PHP/MySQL review system requires HTTPS and an external private `reviews-config.php`. Never publish real database credentials or moderator secrets; `_private/config.example.php` is only a template.

## Fresh installation

Import `_private/schema.sql` through phpMyAdmin. Configure the private PHP file outside the document root and upload the complete `web/reviews` directory, including `.htaccess` files, generated `index.php`, JavaScript and CSS.

## Upgrading a running v1 database

**Back up the MySQL database first**, then apply `_private/migrations/001_developer_replies.sql` using phpMyAdmin **before** deploying the PHP requiring schema v2. Existing review records, ratings, approval status and token hashes are not altered. Apply subsequent migrations in order.

## Moderation

Unlock `?view=moderate` with the existing moderator key. Publish, update or remove one official developer reply per published review. Replies cannot change the reviewer's rating or content. The public API exposes no management token, email, IP or device ID, and the client renders feedback as text rather than HTML.

Run the review tests with the disposable MySQL database in `.github/workflows/reviews.yml`. Run `python3 web/build.py` and `python3 web/check.py` before deployment. A PR does not deploy the site.
