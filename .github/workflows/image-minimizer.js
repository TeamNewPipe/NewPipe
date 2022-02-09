/*
 * Script for minimizing big images (jpg,gif,png) when they are uploaded to GitHub and not edited otherwise
 */
module.exports = async ({github, context}) => {
    const IGNORE_KEY = '<!-- IGNORE IMAGE MINIFY -->';
    const IGNORE_ALT_NAME_END = 'ignoreImageMinify';
    const IMG_MAX_HEIGHT_PX = 600;

    // Get the body of the image
    let initialBody = null;
    if (context.eventName == 'issue_comment') {
        initialBody = context.payload.comment.body;
    } else if (context.eventName == 'issues') {
        initialBody = context.payload.issue.body;
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
    const REGEX_IMAGE_LOOKUP = /\!\[(.*)\]\((https:\/\/[-a-z0-9]+\.githubusercontent\.com\/\d+\/[-0-9a-f]{32,512}\.(jpg|gif|png))\)/gm;

    // Check if we found something
    let foundSimpleImages = REGEX_IMAGE_LOOKUP.test(initialBody);
    if (!foundSimpleImages) {
        console.log('Found no simple images to process');
        return;
    }

    console.log('Found at least one simple image to process');

    // Require the probe lib for getting the image dimensions
    const probe = require('probe-image-size');

    // Try to find and replace the images with minimized ones
    let newBody = await replaceAsync(initialBody, REGEX_IMAGE_LOOKUP, async (match, g1, g2) => {
        console.log(`Found match '${match}'`);
        
        if (g1.endsWith(IGNORE_ALT_NAME_END)) {
            console.log(`Ignoring match '${match}': IGNORE_ALT_NAME_END`);
            return match;
        }
        
        let shouldModifiy = false;
        try {
            console.log(`Probing ${g2}`);
            let probeResult = await probe(g2);
            if (probeResult == null) {
                throw 'No probeResult';
            }
            if (probeResult.hUnits != 'px') {
                throw `Unexpected probeResult.hUnits (expected px but got ${probeResult.hUnits})`;
            }
            
            shouldModifiy = probeResult.height > IMG_MAX_HEIGHT_PX;
        } catch(e) {
            console.log('Probing failed:', e);
            // Immediately abort
            return match;
        }
        
        if (shouldModifiy) {
            console.log(`Modifying match '${match}'`);
            return `<img alt="${g1}" src="${g2}" height=${IMG_MAX_HEIGHT_PX} />`;
        }
        
        console.log(`Match '${match}' is ok/will not be modified`);
        return match;
    });

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
    }

    // Asnyc replace function from https://stackoverflow.com/a/48032528
    async function replaceAsync(str, regex, asyncFn) {
        const promises = [];
        str.replace(regex, (match, ...args) => {
            const promise = asyncFn(match, ...args);
            promises.push(promise);
        });
        const data = await Promise.all(promises);
        return str.replace(regex, () => data.shift());
    }
}
