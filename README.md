# Lightning Browser Adblock Plus Android SDK integration

### Purpose

This is a fork of [Lightning Browser](https://github.com/anthonycr/Lightning-Browser) with integrated [Adblock Plus Android SDK](https://gitlab.com/eyeo/adblockplus/libadblockplus-android). It is created to provide the reference implementation of Adblock Plus Android SDK browser integration.

### Disclaimer

Be aware that there are NO GUARANTEES for stability and availability of the Reference Implementation! We believe that the Lightning Browser is stable, as are the Adblock Plus code and Adblock Plus integration changes, but the resulting application is not tested to be production ready.

### Features

This reference integration inherits all the Lightning Browser [features](https://github.com/anthonycr/Lightning-Browser#features), adding [the Adblock Plus features](https://adblockplus.org/features). The Lightning Browser already contains an adblocking solution. It is shown as `Ad Block Settings` in Settings, while Adblock Plus is added as `Adblock Plus`.
* Adblock Plus with Acceptable Ads.

* AdBlock Plus Settings interface.


### Implementation details
#### Adblock Plus Engine
The Adblock Plus Engine is required for AdblockWebView to function, and should be initialized as early as possible. The Engine is is integrated by adding [imports](https://gitlab.com/eyeo/adblockplus/abp-lightning-browser/-/blob/master/app/src/main/java/acr/browser/lightning/BrowserApp.kt#L27) and [initialization](https://gitlab.com/eyeo/adblockplus/abp-lightning-browser/-/blob/master/app/src/main/java/acr/browser/lightning/BrowserApp.kt#L60) (via `AdblockHelper`) to `BrowserApp.kt`. Please note the [Timber](https://github.com/JakeWharton/timber) initialization [a bit earlier](https://gitlab.com/eyeo/adblockplus/abp-lightning-browser/-/blob/master/app/src/main/java/acr/browser/lightning/BrowserApp.kt#L55).
#### AdblockWebView
AdblockWebView is integrated by adding [imports](https://gitlab.com/eyeo/adblockplus/abp-lightning-browser/-/blame/master/app/src/main/java/acr/browser/lightning/view/LightningView.kt#L47) and changing the construction of WebView to that of [AdblockWebView](https://gitlab.com/eyeo/adblockplus/abp-lightning-browser/-/blame/master/app/src/main/java/acr/browser/lightning/view/LightningView.kt#L216) to `LightningView.kt`. An important part is [the disposal](https://gitlab.com/eyeo/adblockplus/abp-lightning-browser/-/blame/master/app/src/main/java/acr/browser/lightning/view/LightningView.kt#L692) of AdblockWebView.
##### AdblockWebView testing adaptation
To make it easy to switch between Android WebView and AdblockWebView at runtime, a new variable [isAdblockWebView is introduced](https://gitlab.com/eyeo/adblockplus/abp-lightning-browser/-/blob/master/app/src/main/java/acr/browser/lightning/browser/activity/BrowserActivity.kt#L1809) in `BrowserActivity.kt`.
#### Settings
The settings are integrated by adding the [Settings Activity for Adblock Plus](https://gitlab.com/eyeo/adblockplus/abp-lightning-browser/-/blob/master/app/src/main/java/acr/browser/lightning/settings/activity/AdblockPlusSettingsActivity.kt) (`AdblockPlusSettingsActivity.kt`) and adding the [header entry](https://gitlab.com/eyeo/adblockplus/abp-lightning-browser/-/blob/master/app/src/main/res/xml/preferences_headers.xml#L3) for the Activity. There is also a [string resource](https://gitlab.com/eyeo/adblockplus/abp-lightning-browser/-/blob/master/app/src/main/res/values/strings.xml#L263) for Adblock Plus Settings.
We had to create a separate activity to host our settings fragments as a workaround.
#### Resources
There are two preloaded files for the subscriptions: `easylist.txt` and `exceptionrules.txt`. The files are also updated by a [gradle task](https://gitlab.com/eyeo/adblockplus/abp-lightning-browser/-/blob/master/app/build.gradle#L11). Having those files included in the application allows Adblock Plus to function properly even before the subscriptions are downloaded for the first time.
#### Build tools
* The root `build.gradle` now has an entry for [maven repo for Adblockplus](https://gitlab.com/eyeo/adblockplus/abp-lightning-browser/-/blob/master/build.gradle#L20) (it is downloaded as an AAR) and sets `minSdkVersion = 21`.
* `app\build.gradle` now has entries for [adblock plus webview and settings](https://gitlab.com/eyeo/adblockplus/abp-lightning-browser/-/blob/master/app/build.gradle#L219) and [other settings](https://gitlab.com/eyeo/adblockplus/abp-lightning-browser/-/blob/master/app/build.gradle#L273).
#### Testing
There is a [WebViewLoadUrlPerfTest.kt](https://gitlab.com/eyeo/adblockplus/abp-lightning-browser/-/blob/master/app/src/androidTest/java/acr/browser/lightning/test/WebViewLoadUrlPerfTest.kt) test that aims to compare page load times in Android WebView and AdblockWebView, and also does some analysis on the load times.
