NewPipe contribution guidelines
===============================

PLEASE READ THESE GUIDELINES CAREFULLY BEFORE ANY CONTRIBUTION!

## Crash reporting

Do not report crashes in the GitHub issue tracker. NewPipe has an automated crash report system that will ask you to
send a report via e-mail when a crash occurs. This contains all the data we need for debugging, and allows you to even
add a comment to it. You'll see exactly what is sent, the system is 100% transparent.

## Issue reporting/feature requests

* Search the [existing issues](https://github.com/TeamNewPipe/NewPipe/issues) first to make sure your issue/feature
hasn't been reported/requested before.
* Check whether your issue/feature is already fixed/implemented.
* Check if the issue still exists in the latest release/beta version.
* If you are an Android/Java developer, you are always welcome to fix an issue or implement a feature yourself. PRs welcome!
* We use English for development. Issues in other languages will be closed and ignored.
* Please only add *one* issue at a time. Do not put multiple issues into one thread.
* Follow the template! Issues or feature requests not matching the template might be closed.

## Bug Fixing
* If you want to help NewPipe to become free of bugs (this is our utopic goal for NewPipe), you can send us an email to
<a href="mailto:tnp@newpipe.schabi.org">tnp@newpipe.schabi.org</a> to let us know that you intend to help. We'll send you further instructions. You may, on request,
register at our [Sentry](https://sentry.schabi.org) instance (see section "Crash reporting" for more information).

## Translation

* NewPipe is translated via [Weblate](https://hosted.weblate.org/projects/newpipe/strings/). You can log in there
with your GitHub account.
* If the language you want to translate is not on Weblate, you can add it: see [How to add a new language](https://github.com/TeamNewPipe/NewPipe/wiki/How-to-add-a-new-language-to-NewPipe) in the wiki.

## Code contribution

* Stick to NewPipe's style conventions: follow [checkStyle](https://github.com/checkstyle/checkstyle). It will run each time you build the project.
* Do not bring non-free software (e.g. binary blobs) into the project. Also, make sure you do not introduce Google
  libraries.
* Stick to [F-Droid contribution guidelines](https://f-droid.org/wiki/page/Inclusion_Policy).
* Make changes on a separate branch with a meaningful name, not on the master neither dev branch. This is commonly known as *feature branch workflow*. You
  may then send your changes as a pull request (PR) on GitHub.
* When submitting changes, you confirm that your code is licensed under the terms of the
  [GNU General Public License v3](https://www.gnu.org/licenses/gpl-3.0.html).
* Please test (compile and run) your code before you submit changes! Ideally, provide test feedback in the PR
  description. Untested code will **not** be merged!
* Try to figure out yourself why builds on our CI fail.
* Make sure your PR is up-to-date with the rest of the code. Often, a simple click on "Update branch" will do the job,
  but if not, you are asked to rebase the dev branch manually and resolve the problems on your own. You can find help [on the wiki](https://github.com/TeamNewPipe/NewPipe/wiki/How-to-merge-a-PR). That will make the
  maintainers' jobs way easier.
* Please show intention to maintain your features and code after you contributed it. Unmaintained code is a hassle for
  the core developers, and just adds work. If you do not intend to maintain features you contributed, please think again
  about submission, or clearly state that in the description of your PR.
* Respond yourselves if someone requests changes or otherwise raises issues about your PRs.
* Send PR that only cover one specific issue/solution/bug. Do not send PRs that are huge and consists of multiple
  independent solutions.

## Communication

* There is an IRC channel on Freenode which is regularly visited by the core team and other developers:
  [#newpipe](irc:irc.freenode.net/newpipe). [Click here for Webchat](https://webchat.freenode.net/?channels=newpipe)!
* If you want to get in touch with the core team or one of our other contributors you can send an email to
  <a href="mailto:tnp@newpipe.schabi.org">tnp@newpipe.schabi.org</a>. Please do not send issue reports, they will be ignored and remain unanswered! Use the GitHub issue
  tracker described above!
* Feel free to post suggestions, changes, ideas etc. on GitHub or IRC!
