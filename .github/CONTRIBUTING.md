### Please do **not** open pull requests for *new features* now, as we are planning to rewrite large chunks of the code. Only bugfix PRs will be accepted. More details will be announced soon!

NewPipe contribution guidelines
===============================

## Crash reporting

Report crashes through the **automated crash report system** of NewPipe.
This way all the data needed for debugging is included in your bug report for GitHub.
You'll see *exactly* what is sent, be able to add **your comments**, and then send it.

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
* NewPipe uses the [PrettyTime](https://github.com/ocpsoft/prettytime) library to display localized versions of dates and times. It needs to be translated, too. Read [these instructions to add a new language](https://www.ocpsoft.org/prettytime/#section-14) and [this issue](https://github.com/TeamNewPipe/NewPipe/issues/9134) for more info.

## Code contribution

### Guidelines

* Stick to NewPipe's *style conventions* of [checkStyle](https://github.com/checkstyle/checkstyle) and [ktlint](https://github.com/pinterest/ktlint). They run each time you build the project.
* Stick to [F-Droid contribution guidelines](https://f-droid.org/wiki/page/Inclusion_Policy).
* In particular **do not bring non-free software** (e.g. binary blobs) into the project. Make sure you do not introduce any closed-source library from Google.

### Before starting development

* If you want to help out with an existing bug report or feature request, **leave a comment** on that issue saying you want to try your hand at it.
* If there is no existing issue for what you want to work on, **open a new one**  describing the changes you are planning to introduce. This gives the team and the community a chance to give **feedback** before you spend time on something that is already in development, should be done differently, or should be avoided completely.
* Please show **intention to maintain your features** and code after you contribute a PR. Unmaintained code is a hassle for core developers. If you do not intend to maintain features you plan to contribute, please rethink your submission, or clearly state that in the PR description.
* Create PRs that cover only **one specific issue/solution/bug**. Do not create PRs that are huge monoliths and could have been split into multiple independent contributions.
* NewPipe uses [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) to fetch data from services. If you need to change something there, you must test your changes in NewPipe. Telling NewPipe to use your extractor version can be accomplished by editing the `app/build.gradle` file: the comments under the "NewPipe libraries" section of `dependencies` will help you out.

### Creating a Pull Request (PR)

* Make changes on a **separate branch** with a meaningful name, not on the _master_ branch or the _dev_ branch. This is commonly known as *feature branch workflow*. You may then send your changes as a pull request (PR) on GitHub.
* Please **test** (compile and run) your code before submitting changes! Ideally, provide test feedback in the PR description. Untested code will **not** be merged!
* Respond if someone requests changes or otherwise raises issues about your PRs.
* Try to figure out yourself why builds on our CI fail.
* Make sure your PR is **up-to-date** with the rest of the code. Often, a simple click on "Update branch" will do the job, but if not, you must *rebase* your branch on the `dev` branch manually and resolve the conflicts on your own. You can find help [on the wiki](https://github.com/TeamNewPipe/NewPipe/wiki/How-to-merge-a-PR). Doing this makes the maintainers' job way easier.

## IDE setup & building the app

### Basic setup

NewPipe is developed using [Android Studio](https://developer.android.com/studio/). Learn more about how to install it and how it works in the [official documentation](https://developer.android.com/studio/intro). In particular, make sure you have accepted Android Studio's SDK licences. Once Android Studio is ready, setting up the NewPipe project is fairly simple:
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
