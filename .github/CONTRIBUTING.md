### Please do **NOT** open pull requests for *new features* now, as we are currently refactoring the codebase. Only bugfix PRs or refactors will be accepted.

NewPipe contribution guidelines
===============================

**Please make every effort to adhere to these guidelines**  
It is in both your and our best interests that you follow the process so everything can be done quickly and in order.  
If you believe there's anything in these guidelines that is inefficient, missing, unclear, or can otherwise be improved please let us know and we will address it

## AI policy

* Using generative AI to develop new features or making larger code changes is generally prohibited. Please refrain from contributions which are heavily depending on AI generated source code because they are usually lacking a fundamental understanding of the overall project structure and thus come with poor quality. However, you are allowed to use gen. AI if you
  * are aware of the project structure,
  * ensure that the generated code follows the project structure,
  * fully understand the generated code, and
  * review the generated code completely.
* Using AI to find the root cause of bugs and generating small fixes might be acceptable. However, gen. AI often does not fix the underlying problem but is trying to fix the symptoms. If you are using AI to fix bugs, ensure that the root cause is tackled.
* The use of AI to generate documentation is allowed. We ask you to thoroughly check the quality of generated documentation – wrong, misleading or uninformative documentation is useless and wastes the reader's time. Ensure that reasoning is documented.
* Using generative AI to write or fill in PR or issue templates is prohibited. Those texts are often lengthy and miss critical information.
* PRs and issues that do not follow this AI policy can be closed without further explanation.


## Crash reporting

Report crashes through the **automated crash report system** of NewPipe.  
This way all the data needed for debugging is included in your bug report for GitHub.  
You'll see *exactly* what is sent, be able to add **your comments**, and then send it.

## Issue reporting/feature requests

 * **Already reported**? Please search through [existing issues](https://github.com/TeamNewPipe/NewPipe/issues) (both [open **and** closed](https://github.com/TeamNewPipe/NewPipe/issues?q=is%3Aissue)) to make sure your issue isn't already reported. Duplicate issues will be closed.
* **Already fixed**? Check whether your issue/feature is already fixed/implemented in NewPipe.
* **Still relevant**? Check if the issue still exists in the latest version.
* **Can you fix it**? If you are an Android/Java developer, you are always welcome to fix an issue or implement a feature yourself. PRs welcome! See [Code contribution](#code-contribution) for more info.
  * This does not supercede the notice at the top: **only bugfix and refactor PRs will be accepted unless explicitly permitted**
* **Is it in English**? Please do not open issues in other languages. Non-English issues will be ignored or closed unless translated
* **Is it one issue**? Multiple issues require multiple reports, that can be linked to track their statuses.
  * ⚠ **Multiple issues in one issue will be closed and you will be advised to open them as individual issues**
* **The template**: Fill it out, everyone wins. Your issue has a chance of getting fixed.
  * ⚠ **Failure to fill in the template may result in your issue being closed without warning.**


## Translation

* NewPipe is translated via [Weblate](https://hosted.weblate.org/projects/newpipe/strings/). Log in there with your GitHub account, or register.
* Add the language you want to translate if it is not there already: see [How to add a new language](https://github.com/TeamNewPipe/NewPipe/wiki/How-to-add-a-new-language-to-NewPipe) in the wiki.
* NewPipe uses the [PrettyTime](https://github.com/ocpsoft/prettytime) library to display localized versions of dates and times. It needs to be translated, too. Read [these instructions to add a new language](https://www.ocpsoft.org/prettytime/#section-14) and [this issue](https://github.com/TeamNewPipe/NewPipe/issues/9134) for more info.

# Code contribution

## Guidelines

* Stick to NewPipe's *style conventions* of [checkStyle](https://github.com/checkstyle/checkstyle) and [ktlint](https://github.com/pinterest/ktlint). They run each time you build the project.
* Stick to [F-Droid contribution guidelines](https://f-droid.org/wiki/page/Inclusion_Policy).
* In particular **do not bring non-free software** (e.g. binary blobs) into the project. Make sure you do not introduce any closed-source library from Google.

## Before starting development

### Declare intent

If you want to help out with an existing bug report or feature request, **leave a comment** on that issue saying you want to work on it and wait until you are assigned. **We do not welcome unsolicited PRs**.

❗ **YOU MUST** explain how you plan to implement the feature/fix the bug. This lets the team and community give feedback on your solution to know if it's viable or not, so you don't spend time working on something that should be done differently, should be avoided completely, or is otherwise incorrect. 

------


* If there is no existing issue for what you want to work on, **open a new one**, and give a comprehensive explanation of the feature/bug and how you plan to implement your solution, as per what is written above
* Please show **intent to maintain your features** and code after you contribute a PR. Unmaintained code is a hassle for core developers. If you do not intend to maintain features you plan to contribute, please rethink your submission, or clearly state that in the PR description.


## Creating a Pull Request (PR)

See [Forking a repo](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/working-with-forks/fork-a-repo) and [Creating a PR from a fork](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request-from-a-fork) for instructions on how to make a PR.

Fork the repository, and make your changes on a **separate branch** with a meaningful name, *not* on `master` or  `dev`.

**YOU MUST** fill in the PR template. Failure to do so will mean your PR will not be reviewed and may be closed.

### Small and focused PRs

So that PRs can be reviewed quickly and easily, **YOU MUST** ensure your PR targets **only one specific issue/solution/bug**.  
PRs must be focused and kept as small as possible to make them quicker to review, and means they can be reviewed and merged in parallel, instead of one PR containing 5 things that can all blocked from merging because of just 1 of them.

* PRs that just so happen to solve multiple issues and cannot be feasiibly reduced further are an exception

<details><summary><b>Useful info on making reviewable PRs</b></summary>
  <ul>
    <li>https://engineering.joinknack.com/art-and-science-of-reviewable-prs/</li>
    <li>https://zulip.readthedocs.io/en/latest/contributing/reviewable-prs.html</li>
    <li>https://artsy.github.io/blog/2021/03/09/strategies-for-small-focused-pull-requests/</li>
    <li>https://fosdem.org/2026/schedule/event/L7ERNP-prs-maintainers-will-love/</li>
    <li>https://graphite.com/guides/best-practices-managing-pr-size</li>
    <li>https://www.propelcode.ai/blog/pr-size-impact-code-review-quality-data-study</li>
    <li>https://bssw.io/blog_posts/pull-request-size-matters</li>
    <li>https://scicomp.aalto.fi/scicomp/practical-git-prs/</li>
  </ul>
</details>

**Caveat:** our codebase is very old so you will absolutely see things that can easily be refactored as you browse through files.  
Feel free to make small refactors alongside your changes, but ensure they don't clog the PR or add too much cognitive load.

### Splitting work over multiple PRs

PRs that cannot fully address an issue in one PR without being too big must:
  * Be split up into multiple PRs
  * and, if applicable, the issue the PR addresses must also be split up into multiple sub-issues (if it can be logically separated into different issues that aren't just Issue 1, Issue 2, etc. Although this may imply the issue breaks the one issue for one thing rule)

### Average PR review time

PRs should be reviewable within **2-3 dev hrs on average**, and a maxiumum of 6 dev hours.

1 dev hour = 1 hour focused entirely on reviewing the PR without breaks or distractions.

If it would take someone more than 3 hours to fully understand your PR to the point as if they had the written it themselves, then your PR is too big.

* We reserve the right to reject PRs that are too big and request they be split up.

### All changes must be tested

❗ Before opening a PR **YOU MUST** test (compile and run) your changes and ensure they work. Detail precisely what testing has been done in the PR description so that reviewers are easily able to replicate and verify.

NewPipe uses [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) to fetch data from services. If you need to change something there, you must test your changes in NewPipe.
 * See the comments in [libs.versions.toml](https://github.com/TeamNewPipe/NewPipe/blob/dev/gradle/libs.versions.toml#L72) on how to use an extractor build from github
 * See the comment at the bottom of [settings.gradle.kts](https://github.com/TeamNewPipe/NewPipe/blob/dev/settings.gradle.kts) on how to use your own extractor locally
<!-- **TODO:** Update [extractor documentation](https://github.com/TeamNewPipe/documentation) to be accurate and replace with this 
See [here](https://teamnewpipe.github.io/documentation/04_Run_changes_in_App/) for instructions on running NewPipe with your own extractor. -->

* **All NewPipeExtractor code must be tested with unit/integration tests**
  * All bug fixes must include a regression test of said bug that verifies it works with the fix and fails without the fix

<!-- TODO: uncomment when we have appropriate testing infrastructure
* **All NewPipe app code must be tested with unit/integration/UI tests**
  * All bug fixes must include a regression test of said bug that verifies it works with the fix and fails without the fix
  * Whatever steps must be taken manually in the app to test the changes in your PR must be encapsulated in an automated test
  * Likewise any UI changes must be include associated UI tests -->


### Please explain your PR changes

❗ **YOU MUST** include an in-depth comprehensive explanation of your PR changes in the PR description, to save the time of people reviewing your PR

This explanation must be easy to understand so people don't spend hours looking at your code to understand what it does, why it does what it does, or what makes it work.

This will both serve as documentation and time-saving. Keep in mind not everyone knows every part of the codebase or every part of Android, so please make your explanation as straightforward as possible to understand for other _Android developers_. That is, assume readers have good technical expertise but just do not understand that part of the codebase.

The expectation is that reviewers **should not** have to read the code in-depth to understand _how_ the PR implements a feature or fixes a bug. The PR description should do most of the heavy lifting of explaining the code changes. If you keep your PR small and focused this should not be that hard to do.

This will make things easier for everyone and enable your PR to be reviewed by most of the team and not a select few who are already familiar with that area of the codebase, and PRs can be reviewed and merged within 1-2 days instead of being open for weeks and months :).

## Other requirements

* Any PR that changes any part of the UI **must** include screenshots/recordings of **ALL** affected UI screens/components/workflows. Reviewers should not have to manually run your changes to see what the UI changes look like: they should be documented in the PR.
* Respond if team members request changes or otherwise raise issues about your PRs.
  * You are not obligated to do so for things addressed by non-team members (anyone can say anything on PRs); however you are still advised to do so as most of the time their points will be relevant
* It is **your** responsibility to ensure CI builds/tests pass on your PR
  * The only exception is when the failure has nothing to do with your PR
* Please ensure your PR is **up-to-date** with the rest of the code and resolve conflicts as and when they appear.
  * We like to keep clean commit history: so you may update your PR via merging or rebasing as you see fit, but once it's ready to be merged **you must ensure your PR has linear history.**


## IDE setup & building the app

### Basic setup

NewPipe is developed using [Android Studio](https://developer.android.com/studio/). See the [official documentation](https://developer.android.com/studio/intro) for a short brief on how it works. Once installed, setting up NewPipe is fairly simple:
- Clone the NewPipe repository with `git clone https://github.com/TeamNewPipe/NewPipe.git` (or use the link from your own fork, if you want to open a PR).
- Open the folder you just cloned with Android Studio.
- Build and run it just like you would do with any other app, with the green triangle in the top bar.

You may find [SonarLint](https://www.sonarlint.org/intellij)'s **inspections** useful in helping you to write good code and prevent bugs.

### checkStyle setup

The [checkStyle](https://github.com/checkstyle/checkstyle) plugin verifies that Java code abides by the project style. It runs automatically each time you build the project. If you want to view errors directly in the editor, instead of having to skim through the build output, you can install an Android Studio plugin:
- Go to `File -> Settings -> Plugins`, search for `checkstyle` and install `CheckStyle-IDEA`.
- Go to `File -> Settings -> Tools -> Checkstyle`.
- Add NewPipe's configuration file by clicking the `+` in the right toolbar of the "Configuration File" list.
- Under the "Use a local Checkstyle file" bullet, click on `Browse` and, enter `checkstyle` folder under the project's root path and pick the file named `checkstyle.xml`.
- Enable "Store relative to project location" so that moving the directory around does not create issues.
- Insert a description in the top bar, then click `Next` and then `Finish`.
- Activate the configuration file you just added by enabling the checkbox on the left.
- Click `Ok` and you are done.

### ktlint setup

The [ktlint](https://github.com/pinterest/ktlint) plugin does the same job as checkStyle for Kotlin files. Installing the related plugin is as simple as going to `File -> Settings -> Plugins`, searching for `ktlint` and installing `Ktlint (unofficial)`.

## Communication

* You can use a Matrix account to join the NewPipe channel at [#newpipe:matrix.newpipe-ev.de](https://matrix.to/#/#newpipe:matrix.newpipe-ev.de). Some convenient clients, available both for phone and desktop, are listed at that link.
* Alternatively, the #newpipe channel on Libera Chat (`ircs://irc.libera.chat:6697/newpipe`) can also be joined, as it is bridged to the Matrix room. [Click here for webchat](https://web.libera.chat/#newpipe)!
* You can post your suggestions, changes, ideas etc. on either GitHub or Matrix (including via IRC).
