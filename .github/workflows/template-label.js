/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/*
 * Script for commenting on issues/PRs when the template is missing or ignored
 * to encourage proper use of the templates.
 */

const yaml = require('js-yaml');

module.exports = async ({github, context}) => {

    const label = context.payload.label && context.payload.label.name;
    if (!label) return;
    const isPR = !!context.payload.pull_request;
    const number = isPR ? context.payload.pull_request.number : context.payload.issue.number;

    let templateInfo;
    if (isPR) {
        templateInfo = await fetchTemplateAndGenerateComment('Raw template', 'https://raw.githubusercontent.com/TeamNewPipe/NewPipe/refs/heads/dev/.github/PULL_REQUEST_TEMPLATE.md', false);
    } else {
        const templates = {
            'bug report': 'https://raw.githubusercontent.com/TeamNewPipe/NewPipe/refs/heads/dev/.github/ISSUE_TEMPLATE/bug_report.yml',
            'feature request': 'https://raw.githubusercontent.com/TeamNewPipe/NewPipe/refs/heads/dev/.github/ISSUE_TEMPLATE/feature_request.yml'
        };

        const templateParts = await Promise.all(Object.entries(templates).map(async ([templateName, url]) => {
            return await fetchTemplateAndGenerateComment(templateName, url, true);
        }));

        templateInfo = templateParts.join('\n\n');
    }

    let body = '';
    if (label === 'template-ignored') {
      body = 'Please fill in the template completely or at least as much as possible. The team needs more detailed information. Here is the template, in case you deleted parts of it:\n\n' + templateInfo;
    } else if (label === 'template-missing') {
      body = `Thank you for creating this ${isPR ? 'PR' : 'issue'}. Please edit your description and fill in the correct template with the required information:\n\n${templateInfo}`;
    } else {
      // should never happen, but just in case
      return;
    }

    await github.rest.issues.createComment({
      owner: context.repo.owner,
      repo: context.repo.repo,
      issue_number: number,
      body
    });

}

async function fetchTemplateAndGenerateComment(templateName, url, extractBody) {
    const response = await fetch(url);
    if (!response.ok) {
        console.log(`Failed to fetch ${url}: ${response.status} ${response.statusText}`);
        return `<details><summary>${templateName}</summary>\n<a href="${url}" target="_blank">View template</a></details>`;
    }
    let template = await response.text();

    if (extractBody) {
        const issueTemplate = yaml.load(template);
        template = renderIssueForm(issueTemplate.body || []);
    }

    const codeFence = '\n```\n';
    return `<details><summary>${templateName}</summary>\n${codeFence}${template}${codeFence}</details>`;
}

/**
 * Renders the body of a YAML issue template into a Markdown string.
 * @see https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/syntax-for-issue-forms.md
 * @param body
 * @returns string the Markdown representation of the issue form body
 */
function renderIssueForm(body) {
    return body.map(renderFormElement).filter(Boolean).join('\n\n').trim();
}

function renderFormElement(element) {
    const attributes = element.attributes || {};
    switch (element.type) {
        case 'markdown':
            return attributes.value || '';
        case 'input':
        case 'textarea':
        case 'upload':
            return renderTextElement(attributes);
        case 'dropdown':
            return renderDropdown(attributes);
        case 'checkboxes':
            return renderCheckboxes(attributes);
        default:
            return '';
    }
}

function renderTextElement(attributes) {
    const heading = attributes.label ? `### ${attributes.label}` : '';
    const description = attributes.description ? `<!--\n${attributes.description}\n-->` : '';
    const value = attributes.value || attributes.placeholder || '';
    return [heading, description, value].filter(Boolean).join('\n\n');
}

function renderDropdown(attributes) {
    const options = (attributes.options || []).map(option => `- ${option}`).join('\n');
    return [
        attributes.label && `### ${attributes.label}`,
        attributes.description && `<!--\n${attributes.description}\n-->`,
        options
    ].filter(Boolean).join('\n\n');
}

function renderCheckboxes(attributes) {
    const options = (attributes.options || [])
        .map(option => `- [ ] ${option.label || option}`)
        .join('\n');
    return [
        attributes.label && `### ${attributes.label}`,
        attributes.description && `<!--\n${attributes.description}\n-->`,
        options
    ].filter(Boolean).join('\n\n');
}
