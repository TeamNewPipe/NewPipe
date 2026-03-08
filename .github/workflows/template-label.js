/*
 * Script for commenting on issues/PRs when the template is missing or ignored
 */
module.exports = async ({github, context}) => {

    const label = context.payload.label && context.payload.label.name;
    if (!label) return;
    const isPR = !!context.payload.pull_request;
    const number = isPR ? context.payload.pull_request.number : context.payload.issue.number;

    let body = '';
    if (label === 'template-ignored') {
      body = 'Please fill in the template completely or at least as much as possible. The team needs more detailed information.';
    } else if (label === 'template-missing') {
      const kind = isPR ? 'PR' : 'issue';
      let template;
      if (isPR) {
        template = '<details><summary>Raw template</summary>\n```\n' + process.env.PR_TEMPLATE + '\n```\n</details>';
      } else {
        template = '[Bug report template](https://raw.githubusercontent.com/TeamNewPipe/NewPipe/refs/heads/dev/.github/ISSUE_TEMPLATE/bug_report.yml), [Feature request template](https://raw.githubusercontent.com/TeamNewPipe/NewPipe/refs/heads/dev/.github/ISSUE_TEMPLATE/feature_request.yml)';
      }
      body = `Thank you for creating this ${kind}. Please edit your description and add the template with the information: ${template}`;
    } else {
      return;
    }

    await github.rest.issues.createComment({
      owner: context.repo.owner,
      repo: context.repo.repo,
      issue_number: number,
      body
    });

}