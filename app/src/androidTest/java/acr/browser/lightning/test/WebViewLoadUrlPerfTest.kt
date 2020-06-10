/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-present eyeo GmbH
 *
 * Adblock Plus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * Adblock Plus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Adblock Plus.  If not, see <http://www.gnu.org/licenses/>.
 */
package acr.browser.lightning.test

import acr.browser.lightning.MainActivity
import acr.browser.lightning.browser.activity.BrowserActivity
import android.annotation.TargetApi
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.view.KeyEvent
import android.webkit.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import org.adblockplus.libadblockplus.android.Utils
import org.adblockplus.libadblockplus.android.settings.AdblockHelper
import org.adblockplus.libadblockplus.android.webview.AdblockWebView
import org.junit.Assert
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runners.MethodSorters
import timber.log.Timber
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WebViewLoadUrlPerfTest {
    private var webViewIdlingClient: WebViewIdlingClient? = null

    @get:Rule
    val globalTimeout = Timeout.seconds(10000)

    @get:Rule
    val activityRule: ActivityTestRule<MainActivity> = ActivityTestRule(MainActivity::class.java, false, false);

    private var totalPageLoadTime: Long = 0
    private fun addResultToMap(webView: WebView, url: String, loadTime: Long) {
        totalPageLoadTime += loadTime
        if (webView is AdblockWebView) {
            adblockResults[url] = loadTime
        } else {
            systemResults[url] = loadTime
        }
    }

    inner class WebViewIdlingClient(val webView: WebView) : WebViewClient() {
        private var countDownLatch: CountDownLatch? = null
        private var lastPageStartedUrl = ""
        private var startTime = AtomicReference<Long?>(null)
        private var extWebViewClient: WebViewClient? = null

        fun setCountDownLatch(countDownLatch: CountDownLatch?) {
            this.countDownLatch = countDownLatch
            lastPageStartedUrl = ""
        }

        fun resetTimer() {
            startTime = AtomicReference(null)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            Timber.d("onPageStarted called for url %s", url)
            lastPageStartedUrl = url
            if (startTime.get() == null) {
                startTime.set(System.currentTimeMillis())
            }
            if (extWebViewClient != null) {
                extWebViewClient!!.onPageStarted(view, url, favicon)
            } else {
                super.onPageStarted(view, url, favicon)
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            Timber.d("onPageFinished called for urls %s (%b, %b)", url,
                    Utils.getUrlWithoutParams(url).startsWith(Utils.getUrlWithoutParams(lastPageStartedUrl)),
                    countDownLatch != null)
            val startTimeValue = startTime.get()
            // When redirection happens there are several notifications so wee need to check if url matches
            if (Utils.getUrlWithoutParams(url).startsWith(Utils.getUrlWithoutParams(lastPageStartedUrl))
                    && startTimeValue != null) {
                val timeDelta = System.currentTimeMillis() - startTimeValue
                Timber.d("onPageFinished called for urls %s after %d ms (%s)", url, timeDelta,
                        lastPageStartedUrl)
                if (timeDelta > 0) {
                    // We strip urls from params as they may differ between the calls in Adblock and system
                    // WebView (f.e. param can contain timestamp)
                    addResultToMap(webView, Utils.getUrlWithoutParams(url), timeDelta)
                }
                resetTimer()
                countDownLatch!!.countDown()
            }
            if (extWebViewClient != null) {
                extWebViewClient!!.onPageFinished(view, url)
            } else {
                super.onPageFinished(view, url)
            }
        }

        @TargetApi(Build.VERSION_CODES.N)
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return extWebViewClient?.shouldOverrideUrlLoading(view, request)
                    ?: super.shouldOverrideUrlLoading(view, request)
        }

        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            return extWebViewClient?.shouldOverrideUrlLoading(view, url)
                    ?: super.shouldOverrideUrlLoading(view, url)
        }

        override fun onLoadResource(view: WebView, url: String) {
            if (extWebViewClient != null) {
                extWebViewClient!!.onLoadResource(view, url)
            } else {
                super.onLoadResource(view, url)
            }
        }

        @TargetApi(Build.VERSION_CODES.M)
        override fun onPageCommitVisible(view: WebView, url: String) {
            if (extWebViewClient != null) {
                extWebViewClient!!.onPageCommitVisible(view, url)
            } else {
                super.onPageCommitVisible(view, url)
            }
        }

        @TargetApi(Build.VERSION_CODES.O)
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            return extWebViewClient?.onRenderProcessGone(view, detail)
                    ?: super.onRenderProcessGone(view, detail)
        }

        @TargetApi(Build.VERSION_CODES.O_MR1)
        override fun onSafeBrowsingHit(view: WebView, request: WebResourceRequest,
                                       threatType: Int, callback: SafeBrowsingResponse) {
            if (extWebViewClient != null) {
                extWebViewClient!!.onSafeBrowsingHit(view, request, threatType, callback)
            } else {
                super.onSafeBrowsingHit(view, request, threatType, callback)
            }
        }

        override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
            if (extWebViewClient != null) {
                extWebViewClient!!.onReceivedClientCertRequest(view, request)
            } else {
                super.onReceivedClientCertRequest(view, request)
            }
        }

        override fun onTooManyRedirects(view: WebView, cancelMsg: Message, continueMsg: Message) {
            if (extWebViewClient != null) {
                extWebViewClient!!.onTooManyRedirects(view, cancelMsg, continueMsg)
            } else {
                super.onTooManyRedirects(view, cancelMsg, continueMsg)
            }
        }

        override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
            if (extWebViewClient != null) {
                extWebViewClient!!.onReceivedError(view, errorCode, description, failingUrl)
            } else {
                super.onReceivedError(view, errorCode, description, failingUrl)
            }
        }

        @TargetApi(Build.VERSION_CODES.M)
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (extWebViewClient != null) {
                extWebViewClient!!.onReceivedError(view, request, error)
            } else {
                super.onReceivedError(view, request, error)
            }
        }

        override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
            if (extWebViewClient != null) {
                extWebViewClient!!.onFormResubmission(view, dontResend, resend)
            } else {
                super.onFormResubmission(view, dontResend, resend)
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (extWebViewClient != null) {
                extWebViewClient!!.doUpdateVisitedHistory(view, url, isReload)
            } else {
                super.doUpdateVisitedHistory(view, url, isReload)
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            if (extWebViewClient != null) {
                extWebViewClient!!.onReceivedSslError(view, handler, error)
            } else {
                super.onReceivedSslError(view, handler, error)
            }
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
            if (extWebViewClient != null) {
                extWebViewClient!!.onReceivedHttpAuthRequest(view, handler, host, realm)
            } else {
                super.onReceivedHttpAuthRequest(view, handler, host, realm)
            }
        }

        @TargetApi(Build.VERSION_CODES.M)
        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest,
                                         errorResponse: WebResourceResponse) {
            if (extWebViewClient != null) {
                extWebViewClient!!.onReceivedHttpError(view, request, errorResponse)
            } else {
                super.onReceivedHttpError(view, request, errorResponse)
            }
        }

        override fun shouldOverrideKeyEvent(view: WebView, event: KeyEvent): Boolean {
            return extWebViewClient?.shouldOverrideKeyEvent(view, event)
                    ?: super.shouldOverrideKeyEvent(view, event)
        }

        override fun onUnhandledKeyEvent(view: WebView, event: KeyEvent) {
            if (extWebViewClient != null) {
                extWebViewClient!!.onUnhandledKeyEvent(view, event)
            } else {
                super.onUnhandledKeyEvent(view, event)
            }
        }

        override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
            if (extWebViewClient != null) {
                extWebViewClient!!.onScaleChanged(view, oldScale, newScale)
            } else {
                super.onScaleChanged(view, oldScale, newScale)
            }
        }

        override fun onReceivedLoginRequest(view: WebView, realm: String, account: String?, args: String) {
            if (extWebViewClient != null) {
                extWebViewClient!!.onReceivedLoginRequest(view, realm, account, args)
            } else {
                super.onReceivedLoginRequest(view, realm, account, args)
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            return if (extWebViewClient != null) {
                extWebViewClient!!.shouldInterceptRequest(view, request)
            } else {
                super.shouldInterceptRequest(view, request)
            }
        }

        init {
            if (webView is AdblockWebView) {
                extWebViewClient = webView.setWebViewClientForTesting(this)
            } else {
                extWebViewClient = webView.webViewClient
                webView.webViewClient = this
            }
        }
    }

    // Launch activity, clears cookies and cache, then
    fun setUp(isAdblockWebView: Boolean) {
        Timber.d("setUp(%b)", isAdblockWebView)
        totalPageLoadTime = 0
        BrowserActivity.isAdblockWebView = isAdblockWebView
        val countDownLatch = CountDownLatch(2)
        activityRule.launchActivity(Intent())
        val mainActivity: MainActivity = activityRule.getActivity()
        mainActivity.runOnUiThread(Runnable {
            webViewIdlingClient = mainActivity.getWebViewForTesting()?.let { WebViewIdlingClient(it) }
            CookieManager.getInstance().removeAllCookies(ValueCallback { value ->
                if (!value!!) {
                    CookieManager.getInstance().removeAllCookie()
                }
                countDownLatch.countDown()
            })
            webViewIdlingClient!!.webView.clearCache(true)
            countDownLatch.countDown()
        })
        try {
            countDownLatch.await()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        if (mainActivity.getWebViewForTesting() is AdblockWebView) {
            Timber.d("Before FE waitForReady()")
            AdblockHelper.get().provider.waitForReady()
            Timber.d("After FE waitForReady()")
        }
    }

    /**
     * Notes for QA (mostly).
     * Before running the tests one needs to install the test package and main application Android
     * package files (two separate .apk files) to current Android device or emulator. Currently those are:
     * - ./adblock-android-webviewapp/build/outputs/apk/debug/adblock-android-webviewapp-debug.apk
     * - ./adblock-android-webviewapp/build/outputs/apk/androidTest/debug/adblock-android-webviewapp-debug-androidTest.apk
     *
     * To run both test from CLI using ADB run:
     * adb shell am instrument -w -e \
     * class org.adblockplus.libadblockplus.android.webviewapp.test.WebViewEspressoTest \
     * org.adblockplus.libadblockplus.android.webviewapp.test/androidx.test.runner.AndroidJUnitRunner
     *
     * Tu run specific test append #<testName>, f.e.:
     * adb shell am instrument -w -e \
     * *  class org.adblockplus.libadblockplus.android.webviewapp.test.WebViewEspressoTest#_1_testLoadTimeInSystemWebView \
     * *  org.adblockplus.libadblockplus.android.webviewapp.test/androidx.test.runner.AndroidJUnitRunner
    </testName> */
    @LargeTest
    @Test
    @Throws(InterruptedException::class)
    fun _1_testLoadTimeInSystemWebView() {
        setUp(false)
        commonTestLogic()
        Timber.d("testLoadTimeInSystemWebView() total pages load time is %s ms", totalPageLoadTime)
    }

    @LargeTest
    @Test
    @Throws(InterruptedException::class)
    fun _2_testLoadTimeInAdblockWebView() {
        setUp(true)
        commonTestLogic()
        Timber.d("testLoadTimeInAdblockWebView() total pages load time is %s ms", totalPageLoadTime)
        activityRule.getActivity().runOnUiThread(Runnable { (webViewIdlingClient!!.webView as AdblockWebView).dispose(null) })
    }

    @Test // This is not a real test but it just helps to show most delayed pages
    fun _3_compareResults() {
        // Above this threshold delta is suspicious so let's not count it
        val MAX_DELTA_THRESHOLD_MS: Long = 10000 // 10 seconds
        var adblockFinalResult: Long = 0
        var systemFinalResult: Long = 0
        for ((key, systemLoadTime) in systemResults) {
            // Check if entry exists in both maps and has valid value (value > 0)
            val adblockLoadTime = adblockResults[key]
            if (adblockLoadTime != null) {
                if (adblockLoadTime > 0 && systemLoadTime > 0) {
                    val diff = adblockLoadTime - systemLoadTime
                    if (Math.abs(diff) > MAX_DELTA_THRESHOLD_MS) {
                        Timber.d("Adblock is %s for %s of %d ms, rejecting this result!",
                                if (diff > 0) "slower" else "faster", key, Math.abs(diff))
                    } else {
                        adblockFinalResult += adblockLoadTime
                        systemFinalResult += systemLoadTime
                    }
                } else {
                    Timber.w("Skipping url `%s` from measurement due to lack of value!", key)
                }
            } else {
                Timber.w("Skipping url `%s` from measurement as it was completed only in WebView!", key)
            }
        }
        Timber.d("Adblock: compareResults() final pages load time is %s ms", adblockFinalResult)
        Timber.d("System: compareResults() final pages load time is %s ms", systemFinalResult)
        // Acceptance criteria: AdblockWebView adds no more than 10% delay on top of a system WebView
        Assert.assertTrue(adblockFinalResult - systemFinalResult < systemFinalResult / 10)
    }

    @Throws(InterruptedException::class)
    private fun commonTestLogic() {
        val urls: ArrayList<String?> = object : ArrayList<String?>() {
            init {
                add("https://ess.jio.com")
                add("https://www.jiocinema.com")
                add("https://www.jiomart.com")
                add("https://www.jio.com")
                add("https://www.flipkart.com")
                add("https://www.amazon.com")
                add("https://www.news18.com")
                add("https://timesofindia.indiatimes.com/")
                add("https://www.ndtv.com/")
                add("https://www.indiatoday.in/")
                add("https://indianexpress.com/")
                add("https://www.thehindu.com/")
                add("https://www.news18.com/")
                add("https://www.firstpost.com/")
                //add("https://www.business-standard.com/");
                //add("https://www.dnaindia.com/");
                add("https://www.deccanchronicle.com/")
                add("https://www.oneindia.com/")
                add("https://scroll.in/")
                add("https://www.financialexpress.com/")
                //add("https://www.thehindubusinessline.com/");
                add("https://www.outlookindia.com/")
                add("https://www.thequint.com/")
                add("https://www.freepressjournal.in/")
                add("https://telanganatoday.com/")
                add("https://www.asianage.com/")
                add("https://www.tentaran.com/")
                add("https://topyaps.com/")
                add("http://www.socialsamosa.com/")
                add("https://www.techgenyz.com/")
                add("https://www.orissapost.com/")
                add("http://www.teluguglobal.in/")
                add("https://www.yovizag.com/")
                add("http://www.abcrnews.com/")
                add("http://www.navhindtimes.in/")
                add("https://chandigarhmetro.com/")
                add("https://starofmysore.com/")
                //add("http://www.nagpurtoday.in/");
                add("https://leagueofindia.com/")
                add("https://arunachaltimes.in/")
                add("https://www.latestnews1.com/")
                add("https://knnindia.co.in/home")
                add("https://newstodaynet.com/")
                add("https://www.headlinesoftoday.com/")
                add("https://www.gudstory.com/")
                add("http://www.thetimesofbengal.com/")
                add("http://www.risingkashmir.com/")
                add("http://news.statetimes.in")
                //add("https://newswithchai.com/");
            }
        }
        var repetitionCount = 1
        while (repetitionCount-- > 0) {
            for (url in urls) {
                Timber.d("testLoadTime() loads %s", url)
                val countDownLatch = CountDownLatch(1)
                val mainActivity: MainActivity = activityRule.getActivity()
                mainActivity.runOnUiThread(Runnable {
                    webViewIdlingClient!!.setCountDownLatch(countDownLatch)
                    mainActivity.getWebViewForTesting()?.loadUrl(url)
                })
                val hasFinished = countDownLatch.await(MAX_PAGE_LOAD_WAIT_TIME_SEC.toLong(), TimeUnit.SECONDS)
                if (!hasFinished) {
                    webViewIdlingClient!!.resetTimer()
                    Timber.w("Skipping url `%s` from measurement due to too long loading time in %s!",
                            url, if (mainActivity.getWebViewForTesting() is AdblockWebView) "AdblockWebView" else "WebView")
                }
            }
        }
    }

    companion object {
        private const val MAX_PAGE_LOAD_WAIT_TIME_SEC = 30

        // static data to keep state (results) between tests _1_ and _2_, then print in and _3_
        private val adblockResults: MutableMap<String, Long> = HashMap()
        private val systemResults: MutableMap<String, Long> = HashMap()
    }
}