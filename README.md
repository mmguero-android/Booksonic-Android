

Booksonic App
========
[![googleplay-shield]][googleplay-link]
[![googleplaydownloads-shield]][googleplay-link]
[![issues-shield]](issues)
[![License][license-shield]](LICENSE.md)
[![reddit-shield]][reddit-link]
[![twitter-shield]][twitter-link]

[![Buy me a coffee][buymeacoffee-shield]][buymeacoffee-link]


What is Booksonic?
-----------------

Booksonic is a platform for accessing the audibooks you own wherever you are.
At the moment the platform consists of

 - **[Booksonic Air][booksonic-air-link]** - A server for streaming your audiobooks, successor to the original Booksonic server. Based on Airsonic.
 - **[Booksonic App][booksonic-app-link]** - An android app for connection to Booksonic servers. Available on [Google Play][googleplay-link]. Based on DSub 
 
**Upcoming:**
 - **Booksonic Bridge** - This is an upcoming serverside software that will allow you to use third party servers like Plex, Emby, Youtube etc. It is currently in closed beta and no timeframe is given.

**Extra tools**
 - **[Booksonic ODM2Meta](https://github.com/popeen/Booksonic-Export-Booksonic-Metadata-from-ODM-Files)** - A script for quickly converting your ODM files to metadata that can be used by Booksonic. In the future this will not be needed as Booksonic will soon support ODM files natively.
 - **[Bulk convert UTF8](https://github.com/popeen/Booksonic-Bulk-convert-to-UTF8)** - A script for bulk conversion of meta files to UTF-8
 - **[Booksonic Library Editor](https://github.com/galacticat/booksonic-library-editor)** - A third party Library editor

While there is no iOS app available (yet) Booksonic fully supports the Subsonic API so you will be able to use it with any app that supports that, you will miss out on some Booksonic specific features but  you will be able to listen without problem.

More information about the project can be found at [booksonic.org](https://booksonic.org)

What is Booksonic App?
-----------------
First of all, credit where credit is due, the Booksonic App is not built from scratch, instead it is building on top of the amazing work done by Scott Jackson over at [DSub](https://github.com/daneren2005/Subsonic/) .

Now then, the Booksonic App is the best way to use Booksonic. It connects to one or more Booksonic servers or even any other server compatible with the Subsonic api.

Some notable features are:

 - Supports multiple servers
 - Offline support
 - Variable playback speed
 - Sleep timer with shake to reset functionality
 - and much more

 Building from Source with Docker
 -----------------

 You can generate an unofficial, unsigned Booksonic APK from source using [Docker Android Build Box](https://github.com/mingchen/docker-android-build-box):

 ```
 $ docker pull mingc/android-build-box:latest
 ...

 $ cd ./Booksonic-Android

 $ docker run --rm -v $(pwd):/project mingc/android-build-box bash -c 'cd /project; bash ./gradlew assembleRelease'
 ...

 $ ls -lh ./app/build/outputs/apk/*/*/*.apk
 -rw-r--r-- 1 root root 7.1M Jun 18 14:34 './app/build/outputs/apk/floss/debug/Booksonic 2003.2-floss-debug.apk'
 -rw-r--r-- 1 root root 3.7M Jun 18 14:33 './app/build/outputs/apk/floss/fix/Booksonic 2003.2-floss-fix-unsigned.apk'
 -rw-r--r-- 1 root root 3.7M Jun 18 14:49 './app/build/outputs/apk/floss/release/Booksonic 2003.2-floss-release-unsigned.apk'
 -rw-r--r-- 1 root root 7.1M Jun 18 14:34 './app/build/outputs/apk/google/debug/Booksonic 2003.2-google-debug.apk'
 -rw-r--r-- 1 root root 3.8M Jun 18 14:34 './app/build/outputs/apk/google/fix/Booksonic 2003.2-google-fix-unsigned.apk'
 -rw-r--r-- 1 root root 3.8M Jun 18 14:50 './app/build/outputs/apk/google/release/Booksonic 2003.2-google-release-unsigned.apk'
 ```
 
License
-------

Booksonic App is free software and licensed under the [GNU General Public License version 3](http://www.gnu.org/copyleft/gpl.html). 

Usage
-----

All Booksonic downloads can be found at
https://booksonic.org/download

Pull requests
---------
All pull requests are welcome to any of the Booksonic projects

Community
---------
If you have any questions or ideas, come visit us at [/r/booksonic](https://reddit.com/r/booksonic) over on Reddit

[booksonic-air-link]: https://github.com/popeen/Booksonic-Air
[booksonic-app-link]: https://github.com/popeen/Booksonic-App

[googleplay-shield]: https://img.shields.io/badge/released-google%20play-green.svg
[googleplay-link]: https://play.google.com/store/apps/details?id=github.popeen.dsub
[googleplaydownloads-shield]: https://img.shields.io/badge/google%20play%20downloads-10.000%2B-blue.svg

[issues-shield]: https://img.shields.io/github/issues-raw/popeen/Booksonic-App.svg
[license-shield]: https://img.shields.io/github/license/popeen/Booksonic-App.svg

[reddit-shield]: https://img.shields.io/reddit/subreddit-subscribers/booksonic?style=social
[reddit-link]: https://reddit.com/r/booksonic

[twitter-shield]: https://img.shields.io/twitter/follow/popeencom?style=social
[twitter-link]: https://twitter.com/popeencom

[buymeacoffee-shield]: https://www.buymeacoffee.com/assets/img/guidelines/download-assets-sm-2.svg
[buymeacoffee-link]: https://www.buymeacoffee.com/popeen

