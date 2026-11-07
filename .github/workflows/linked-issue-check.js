/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

module.exports = async ({github, context}) => {
    const pullRequest = context.payload.pull_request;
    const query = `query($owner: String!, $repo: String!, $number: Int!) {
        repository(owner: $owner, name: $repo) {
            pullRequest(number: $number) {
                author {
                    login
                }
                closingIssuesReferences(first: 100) {
                    nodes {
                        number
                        repository {
                            nameWithOwner
                        }
                        assignees(first: 10) {
                            nodes {
                                login
                            }
                        }
                    }
                }
            }
        }
    }`;

    const result = await github.graphql(query, {
        owner: context.repo.owner,
        repo: context.repo.repo,
        number: pullRequest.number
    });
    const linkedIssues = result.repository.pullRequest.closingIssuesReferences.nodes;
    const authorLogin = result.repository.pullRequest.author.login;
    const authorIsAssigned = linkedIssues.some(issue =>
        issue.assignees.nodes.some(assignee => assignee.login === authorLogin)
    );

    console.log(
        `Found ${linkedIssues.length} linked issue(s); ` +
        `the PR author ${authorLogin} is assigned to at least one: ${authorIsAssigned}`
    );

    if (authorIsAssigned) {
        return;
    }

    await github.rest.issues.addLabels({
        owner: context.repo.owner,
        repo: context.repo.repo,
        issue_number: pullRequest.number,
        labels: ['template-ignored']
    });

    let body;
    if (linkedIssues.length === 0) {
        body = 'Please link this PR to an existing issue you are assigned to. Assignees are expected to discuss the details of the PR beforehand. This PR will be closed if it is not linked to an issue.';
    } else {
        body = 'Please link this PR to an existing issue you are assigned to. This PR might be closed if the details of this PR were not agreed upon beforehand.';
    }

    await github.rest.issues.createComment({
        owner: context.repo.owner,
        repo: context.repo.repo,
        issue_number: pullRequest.number,
        body: body
    });

    return false;

};
