NewPipe contribution guidelines
===============================

PLEASE READ THESE GUIDELINES CAREFULLY BEFORE ANY CONTRIBUTION!

## Crash reporting

Do not report crashes in the GitHub issue tracker. NewPipe has an automated crash report system that will ask you to send a report via e-mail when a crash occurs. This contains all the data we need for debugging, and allows you to even add a comment to it. You'll see exactly what is sent, the system is 100% transparent.

## Issue reporting/feature requests

* Search the [existing issues](https://github.com/TeamNewPipe/NewPipe/issues) first to make sure your issue/feature hasn't been reported/requested before
* Check whether your issue/feature is already fixed/implemented
* Check if the issue still exists in the latest release/beta version
* If you are an Android/Java developer, you are always welcome to fix/implement an issue/a feature yourself. PRs welcome!
* We use English for development. Issues in other languages will be closed and ignored.
* Please only add *one* issue at a time. Do not put multiple issues into one thread.

## Bug Fixing
* If you want to help NewPipe to become free of bugs (this is our utopic goal for NewPipe), you can send us an email to tnp@newpipe.schabi.org to let me know that you intend to help. We'll send you further instructions. You may, on request, register at our [Sentry](https://sentry.schabi.org) instance (see section "Crash reporting" for more information.

## Translation

* NewPipe can be translated via [Weblate](https://hosted.weblate.org/projects/newpipe/strings/). You can log in there with your GitHub account.

## Code contribution

* Stick to NewPipe's style conventions (well, just look the other code and then do it the same way :))
* Do not bring non-free software (e.g., binary blobs) into the project. Also, make sure you do not introduce Google libraries.
* Stick to [F-Droid contribution guidelines](https://f-droid.org/wiki/page/Inclusion_Policy)
* Make changes on a separate branch, not on the master branch. This is commonly known as *feature branch workflow*. You may then send your changes as a pull request on GitHub. Patches to the email address mentioned in this document might not be considered, GitHub is the primary platform. (This only affects you if you are a member of TeamNewPipe)
* When submitting changes, you confirm that your code is licensed under the terms of the [GNU General Public License v3](https://www.gnu.org/licenses/gpl-3.0.html).
* Please test (compile and run) your code before you submit changes! Ideally, provide test feedback in the PR description. Untested code will **not** be merged!
* Try to figure out yourself why builds on our CI fail.
* Make sure your PR is up-to-date with the rest of the code. Often, a simple click on "Update branch" will do the job, but if not, you are asked to merge the master branch manually and resolve the problems on your own. That will make the maintainers' jobs way easier.
* Please show intention to maintain your features and code after you contributed it. Unmaintained code is a hassle for the core developers, and just adds work. If you do not intend to maintain features you contributed, please think again about submission, or clearly state that in the description of your PR.
* Respond yourselves if someone requests changes or otherwise raises issues about your PRs.
* Check if your contributions align with the [fdroid inclusion guidelines](https://f-droid.org/en/docs/Inclusion_Policy/).
* Check if your submission can be build with the current fdroid build server setup.

## Communication

* WE DO NOW HAVE A MAILING LIST: [newpipe@list.schabi.org](https://list.schabi.org/cgi-bin/mailman/listinfo/newpipe).
* There is an IRC channel on Freenode which is regularly visited by the core team and other developers: [#newpipe](irc:irc.freenode.net/newpipe). [Click here for Webchat](https://webchat.freenode.net/?channels=newpipe)!
* If you want to get in touch with the core team or one of our other contributors you can send an email to tnp(at)schabi.org. Please do not send issue reports, they will be ignored and remain unanswered! Use the GitHub issue tracker described above!
* Feel free to post suggestions, changes, ideas etc. on GitHub, IRC or the mailing list!
