/*
 * Script for minimizing big images (jpg,gif,png) when they are uploaded to GitHub and not edited otherwise
 */
module.exports = async ({github, context}) => {
    const IGNORE_KEY = '<!-- IGNORE IMAGE MINIFY -->';
    const IGNORE_ALT_NAME_END = 'ignoreImageMinify';
    // Targeted maximum height
    const IMG_MAX_HEIGHT_PX = 600;
    // maximum width of GitHub issues/comments
    const IMG_MAX_WIDTH_PX = 800;
    // all images that have a lower aspect ratio (-> have a smaller width) than this will be minimized
    const MIN_ASPECT_RATIO = IMG_MAX_WIDTH_PX / IMG_MAX_HEIGHT_PX

    // Get the body of the image
    let initialBody = null;
    if (context.eventName == 'issue_comment') {
        initialBody = context.payload.comment.body;
    } else if (context.eventName == 'issues') {
        initialBody = context.payload.issue.body;
    } else if (context.eventName == 'pull_request') {
        initialBody = context.payload.pull_request.body;
    } else {
        console.log('Aborting: No body found');
        return;
    }
    console.log(`Found body: \n${initialBody}\n`);

    // Check if we should ignore the currently processing element
    if (initialBody.includes(IGNORE_KEY)) {
        console.log('Ignoring: Body contains IGNORE_KEY');
        return;
    }

    // Regex for finding images (simple variant) ![ALT_TEXT](https://*.githubusercontent.com/<number>/<variousHexStringsAnd->.<fileExtension>)
    const REGEX_USER_CONTENT_IMAGE_LOOKUP = /\!\[([^\]]*)\]\((https:\/\/[-a-z0-9]+\.githubusercontent\.com\/\d+\/[-0-9a-f]{32,512}\.(jpg|gif|png))\)/gm;
    const REGEX_ASSETS_IMAGE_LOCKUP = /\!\[([^\]]*)\]\((https:\/\/github\.com\/[-\w\d]+\/[-\w\d]+\/assets\/\d+\/[\-0-9a-f]{32,512})\)/gm;

    // Check if we found something
    let foundSimpleImages = REGEX_USER_CONTENT_IMAGE_LOOKUP.test(initialBody)
        || REGEX_ASSETS_IMAGE_LOCKUP.test(initialBody);
    if (!foundSimpleImages) {
        console.log('Found no simple images to process');
        return;
    }

    console.log('Found at least one simple image to process');

    // Require the probe lib for getting the image dimensions
    const probe = require('probe-image-size');
    
    var wasMatchModified = false;

    // Try to find and replace the images with minimized ones
    let newBody = await replaceAsync(initialBody, REGEX_USER_CONTENT_IMAGE_LOOKUP, minimizeAsync);
    newBody = await replaceAsync(newBody, REGEX_ASSETS_IMAGE_LOCKUP, minimizeAsync);
    
    if (!wasMatchModified) {
        console.log('Nothing was modified. Skipping update');
        return;
    }

    // Update the corresponding element
    if (context.eventName == 'issue_comment') {
        console.log('Updating comment with id', context.payload.comment.id);
        await github.rest.issues.updateComment({
            comment_id: context.payload.comment.id,
            owner: context.repo.owner,
            repo: context.repo.repo,
            body: newBody
        })
    } else if (context.eventName == 'issues') {
        console.log('Updating issue', context.payload.issue.number);
        await github.rest.issues.update({
            issue_number: context.payload.issue.number,
            owner: context.repo.owner,
            repo: context.repo.repo,
            body: newBody
        });
    } else if (context.eventName == 'pull_request') {
        console.log('Updating pull request', context.payload.pull_request.number);
        await github.rest.pulls.update({
            pull_number: context.payload.pull_request.number,
            owner: context.repo.owner,
            repo: context.repo.repo,
            body: newBody
        });
    }

    // Async replace function from https://stackoverflow.com/a/48032528
    async function replaceAsync(str, regex, asyncFn) {
        const promises = [];
        str.replace(regex, (match, ...args) => {
            const promise = asyncFn(match, ...args);
            promises.push(promise);
        });
        const data = await Promise.all(promises);
        return str.replace(regex, () => data.shift());
    }

    async function minimizeAsync(match, g1, g2) {
            console.log(`Found match '${match}'`);

            if (g1.endsWith(IGNORE_ALT_NAME_END)) {
                console.log(`Ignoring match '${match}': IGNORE_ALT_NAME_END`);
                return match;
            }

            let probeAspectRatio = 0;
            let shouldModify = false;
            try {
                console.log(`Probing ${g2}`);
                let probeResult = await probe(g2);
                if (probeResult == null) {
                    throw 'No probeResult';
                }
                if (probeResult.hUnits != 'px') {
                    throw `Unexpected probeResult.hUnits (expected px but got ${probeResult.hUnits})`;
                }
                if (probeResult.height <= 0) {
                    throw `Unexpected probeResult.height (height is invalid: ${probeResult.height})`;
                }
                if (probeResult.wUnits != 'px') {
                    throw `Unexpected probeResult.wUnits (expected px but got ${probeResult.wUnits})`;
                }
                if (probeResult.width <= 0) {
                    throw `Unexpected probeResult.width (width is invalid: ${probeResult.width})`;
                }
                console.log(`Probing resulted in ${probeResult.width}x${probeResult.height}px`);

                probeAspectRatio = probeResult.width / probeResult.height;
                shouldModify = probeResult.height > IMG_MAX_HEIGHT_PX && probeAspectRatio < MIN_ASPECT_RATIO;
            } catch(e) {
                console.log('Probing failed:', e);
                // Immediately abort
                return match;
            }

            if (shouldModify) {
                wasMatchModified = true;
                console.log(`Modifying match '${match}'`);
                return `<img alt="${g1}" src="${g2}" width=${Math.min(600, Math.floor(IMG_MAX_HEIGHT_PX * probeAspectRatio))} />`;
            }

            console.log(`Match '${match}' is ok/will not be modified`);
            return match;
        }
}
