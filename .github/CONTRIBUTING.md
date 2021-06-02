NewPipe contribution guidelines
===============================

## Crash reporting

Report crashes through the automated crash report system of NewPipe.
This way all the data needed for debugging is included in your bugreport for GitHub.
You'll see exactly what is sent, be able to add your comments, and then send it.

## Issue reporting/feature requests

* **Already reported**? Browse the [existing issues](https://github.com/TeamNewPipe/NewPipe/issues) to make sure your issue/feature hasn't been reported/requested.
* **Already fixed**? Check whether your issue/feature is already fixed/implemented.
* **Still relevant**? Check if the issue still exists in the latest release/beta version.
* **Can you fix it**? If you are an Android/Java developer, you are always welcome to fix an issue or implement a feature yourself. PRs welcome! See [Code contribution](#code-contribution) for more info.
* **Is it in English**? Issues in other languages will be ignored unless someone translates them.
* **Is it one issue**? Multiple issues require multiple reports, that can be linked to track their statuses.
* **The template**: Fill it out, everyone wins. Your issue has a chance of getting fixed.


## Translation

* NewPipe is translated via [Weblate](https://hosted.weblate.org/projects/newpipe/strings/). Log in there with your GitHub account, or register.
* Add the language you want to translate if it is not there already: see [How to add a new language](https://github.com/TeamNewPipe/NewPipe/wiki/How-to-add-a-new-language-to-NewPipe) in the wiki.

## Code contribution

* If you want to help out with an existing bug report or feature request, leave a comment on that issue saying you want to try your hand at it.
* If there is no existing issue for what you want to work on, open a new one describing your changes. This gives the team and the community a chance to give feedback before you spend time on something that is already in development, should be done differently, or should be avoided completely.
* Stick to NewPipe's style conventions of [checkStyle](https://github.com/checkstyle/checkstyle). It runs each time you build the project.
* Do not bring non-free software (e.g. binary blobs) into the project. Make sure you do not introduce Google
  libraries.
* Stick to [F-Droid contribution guidelines](https://f-droid.org/wiki/page/Inclusion_Policy).
* Make changes on a separate branch with a meaningful name, not on the _master_ branch or the _dev_ branch. This is commonly known as *feature branch workflow*. You may then send your changes as a pull request (PR) on GitHub.
* Please test (compile and run) your code before submitting changes! Ideally, provide test feedback in the PR description. Untested code will **not** be merged!
* Make sure your PR is up-to-date with the rest of the code. Often, a simple click on "Update branch" will do the job, but if not, you must rebase the dev branch manually and resolve the problems on your own. You can find help [on the wiki](https://github.com/TeamNewPipe/NewPipe/wiki/How-to-merge-a-PR). That makes the maintainers' jobs way easier.
* Please show intention to maintain your features and code after you contribute a PR. Unmaintained code is a hassle for core developers. If you do not intend to maintain features you plan to contribute, please rethink your submission, or clearly state that in the PR description.
* Respond if someone requests changes or otherwise raises issues about your PRs.
* Send PRs that only cover one specific issue/solution/bug. Do not send PRs that are huge and consist of multiple independent solutions.
* Try to figure out yourself why builds on our CI fail.

## Communication

* The #newpipe channel on Libera Chat (`ircs://irc.libera.chat:6697/newpipe`) has the core team and other developers in it. [Click here for webchat](https://web.libera.chat/#newpipe)!
* You can also use a Matrix account to join the NewPipe channel at [#newpipe:libera.chat](https://matrix.to/#/#newpipe:libera.chat).
* Post suggestions, changes, ideas etc. on GitHub or IRC.
