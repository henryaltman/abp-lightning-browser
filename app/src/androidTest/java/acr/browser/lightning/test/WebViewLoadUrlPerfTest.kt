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

import acr.browser.lightning.BuildConfig
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
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import junit.framework.Assert.fail
import org.adblockplus.libadblockplus.android.Utils
import org.adblockplus.libadblockplus.android.settings.AdblockHelper
import org.adblockplus.libadblockplus.android.webview.AdblockWebView
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runners.MethodSorters
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WebViewLoadUrlPerfTest {
    private lateinit var webViewIdlingClient: WebViewIdlingClient

    @get:Rule
    val globalTimeout = Timeout(15, TimeUnit.MINUTES)

    @get:Rule
    val activityRule: ActivityTestRule<MainActivity> = ActivityTestRule(MainActivity::class.java,
            false, false)

    val MAX_PAGE_LOAD_WAIT_TIME_SECONDS = BuildConfig.PERF_MAX_PAGE_LOAD_TIME ?: 30
    val MAX_DELTA_THRESHOLD_SECONDS =  BuildConfig.PERF_MAX_DELTA ?: 10
    val urls = BuildConfig.PERF_TEST_URLS?.toList() ?: listOf(
            "https://ess.jio.com",
            "https://www.jiocinema.com",
            "https://www.jiomart.com",
            "https://www.jio.com",
            "https://www.flipkart.com",
            "https://www.amazon.com",
            "https://www.news18.com",
            "https://timesofindia.indiatimes.com/",
            "https://www.ndtv.com/",
            "https://www.indiatoday.in/",
            "https://www.thehindu.com/",
            "https://www.firstpost.com/",
            "https://www.deccanchronicle.com/",
            "https://www.oneindia.com/",
            "https://scroll.in/",
            "https://www.financialexpress.com/",
            "https://www.outlookindia.com/",
            "https://www.thequint.com/",
            "https://www.freepressjournal.in/",
            "https://telanganatoday.com/",
            "https://www.asianage.com/",
            "https://www.tentaran.com/",
            "https://topyaps.com/",
            "http://www.socialsamosa.com/",
            "https://www.techgenyz.com/",
            "https://www.orissapost.com/",
            "http://www.teluguglobal.in/",
            "https://www.yovizag.com/",
            "http://www.abcrnews.com/",
            "http://www.navhindtimes.in/",
            "https://chandigarhmetro.com/",
            "https://starofmysore.com/",
            "https://leagueofindia.com/",
            "https://arunachaltimes.in/",
            "https://www.latestnews1.com/",
            "https://knnindia.co.in/home",
            "https://newstodaynet.com/",
            "https://www.headlinesoftoday.com/",
            "https://www.gudstory.com/",
            "http://www.thetimesofbengal.com/",
            "http://www.risingkashmir.com/",
            "http://news.statetimes.in",
            "http://www.thenorthlines.com/",
            "https://thelivenagpur.com/",
            "https://doonhorizon.in/",
            "http://creativebharat.com/",
            "https://www.emitpost.com/",
            "newsdeets.com",
            "timesnowindia.com",
            "sinceindependence.com",
            "newsblare.com",
            "delhincrnews.in",
            "liveatnews.com",
            "democraticjagat.com",
            "bilkulonline.com",
            "quintdaily.com",
            "pressmirchi.com",
            "notabletoday.blogspot.com",
            "indiannewsqld.com.au",
            "udaybulletin.com",
            "jaianndata.com",
            "campusbeat.in",
            "ytosearch.com",
            "thenewshimachal.com",
            "sportskanazee.com",
            "absoni12.blogspot.com",
            "atulyaloktantranews.com"
    ).distinct()

    private var totalPageLoadTime = 0L
    private fun addResultToMap(webView: WebView, url: String, loadTime: Long) {
        Timber.d("Page `%s` has loaded in %s within ~%d seconds", url,
                (if (webView is AdblockWebView) "AdblockWebView" else "WebView"), loadTime / 1000);
        totalPageLoadTime += loadTime
        if (webView is AdblockWebView) {
            adblockResults[url] = loadTime
        } else {
            systemResults[url] = loadTime
        }
    }

    inner class WebViewIdlingClient(val webView: WebView, val extWebViewClient: WebViewClient?) : WebViewClient() {
        private var countDownLatch: CountDownLatch? = null
        private var lastPageStartedUrl = ""
        private val startTime = AtomicReference<Long?>()

        init {
            webView.webViewClient = this
        }

        fun setCountDownLatch(countDownLatch: CountDownLatch) {
            this.countDownLatch = countDownLatch
            lastPageStartedUrl = ""
        }

        fun resetTimer() {
            startTime.set(null)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            Timber.d("onPageStarted called for url %s", url)
            lastPageStartedUrl = url
            startTime.compareAndSet(null, System.currentTimeMillis())
            extWebViewClient?.onPageStarted(view, url, favicon)
                    ?: super.onPageStarted(view, url, favicon)
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
            extWebViewClient?.onPageFinished(view, url)
                    ?: super.onPageFinished(view, url)
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
            extWebViewClient?.onLoadResource(view, url)
                    ?: super.onLoadResource(view, url)
        }

        @TargetApi(Build.VERSION_CODES.M)
        override fun onPageCommitVisible(view: WebView, url: String) {
            extWebViewClient?.onPageCommitVisible(view, url)
                    ?: super.onPageCommitVisible(view, url)
        }

        @TargetApi(Build.VERSION_CODES.O)
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            return extWebViewClient?.onRenderProcessGone(view, detail)
                    ?: super.onRenderProcessGone(view, detail)
        }

        @TargetApi(Build.VERSION_CODES.O_MR1)
        override fun onSafeBrowsingHit(view: WebView, request: WebResourceRequest,
                                       threatType: Int, callback: SafeBrowsingResponse) {
            extWebViewClient?.onSafeBrowsingHit(view, request, threatType, callback)
                    ?: super.onSafeBrowsingHit(view, request, threatType, callback)
        }

        override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
            extWebViewClient?.onReceivedClientCertRequest(view, request)
                    ?: super.onReceivedClientCertRequest(view, request)
        }

        override fun onTooManyRedirects(view: WebView, cancelMsg: Message, continueMsg: Message) {
            extWebViewClient?.onTooManyRedirects(view, cancelMsg, continueMsg)
                    ?: super.onTooManyRedirects(view, cancelMsg, continueMsg)
        }

        override fun onReceivedError(view: WebView, errorCode: Int, description: String,
                                     failingUrl: String) {
            extWebViewClient?.onReceivedError(view, errorCode, description, failingUrl)
                    ?: super.onReceivedError(view, errorCode, description, failingUrl)
        }

        @TargetApi(Build.VERSION_CODES.M)
        override fun onReceivedError(view: WebView, request: WebResourceRequest,
                                     error: WebResourceError) {
            extWebViewClient?.onReceivedError(view, request, error)
                    ?: super.onReceivedError(view, request, error)
        }

        override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
            extWebViewClient?.onFormResubmission(view, dontResend, resend)
                    ?: super.onFormResubmission(view, dontResend, resend)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            extWebViewClient?.doUpdateVisitedHistory(view, url, isReload)
                    ?: super.doUpdateVisitedHistory(view, url, isReload)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            extWebViewClient?.onReceivedSslError(view, handler, error)
                    ?: super.onReceivedSslError(view, handler, error)
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String,
                                               realm: String) {
            extWebViewClient?.onReceivedHttpAuthRequest(view, handler, host, realm)
                    ?: super.onReceivedHttpAuthRequest(view, handler, host, realm)
        }

        @TargetApi(Build.VERSION_CODES.M)
        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest,
                                         errorResponse: WebResourceResponse) {
            extWebViewClient?.onReceivedHttpError(view, request, errorResponse)
                    ?: super.onReceivedHttpError(view, request, errorResponse)
        }

        override fun shouldOverrideKeyEvent(view: WebView, event: KeyEvent): Boolean {
            return extWebViewClient?.shouldOverrideKeyEvent(view, event)
                    ?: super.shouldOverrideKeyEvent(view, event)
        }

        override fun onUnhandledKeyEvent(view: WebView, event: KeyEvent) {
            extWebViewClient?.onUnhandledKeyEvent(view, event)
                    ?: super.onUnhandledKeyEvent(view, event)
        }

        override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
            extWebViewClient?.onScaleChanged(view, oldScale, newScale)
                    ?: super.onScaleChanged(view, oldScale, newScale)
        }

        override fun onReceivedLoginRequest(view: WebView, realm: String, account: String?, args: String) {
            extWebViewClient?.onReceivedLoginRequest(view, realm, account, args)
                    ?: super.onReceivedLoginRequest(view, realm, account, args)
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            return extWebViewClient?.shouldInterceptRequest(view, request)
                    ?: super.shouldInterceptRequest(view, request)
        }
    }

    // Launch activity, clears cookies and cache, then
    fun setUp(isAdblockWebView: Boolean) {
        Timber.d("setUp(%b)", isAdblockWebView)
        totalPageLoadTime = 0
        BrowserActivity.isAdblockWebView = isAdblockWebView
        val countDownLatch = CountDownLatch(2)
        activityRule.launchActivity(Intent())
        val mainActivity = activityRule.getActivity().apply {
            runOnUiThread {
                webViewIdlingClient = WebViewIdlingClient(getWebViewForTesting(), getWebViewClientForTesting())
                CookieManager.getInstance().removeAllCookies(ValueCallback { cookieRemoved ->
                    if (!cookieRemoved!!) {
                        CookieManager.getInstance().removeAllCookie()
                    }
                    countDownLatch.countDown()
                })
                webViewIdlingClient.webView.clearCache(true)
                countDownLatch.countDown()
            }
        }
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
     * - ./app/build/outputs/apk/lightningLite/debug/app-lightningLite-debug.apk
     * - ./app/build/outputs/apk/androidTest/lightningLite/debug/app-lightningLite-debug-androidTest.apk
     *
     * To run both test from CLI using ADB run:
     * adb shell am instrument -w -e class 'acr.browser.lightning.test.WebViewLoadUrlPerfTest' \
     *   acr.browser.barebones.test/androidx.test.runner.AndroidJUnitRunner
     */
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
        activityRule.getActivity().runOnUiThread {
            (webViewIdlingClient.webView as AdblockWebView).dispose(null)
        }
    }

    @Test // This is not a real test but it just helps to show most delayed pages
    fun _3_compareResults() {
        // Above this threshold delta is suspicious so let's not count it
        val SECONDS_IN_MS = 1000L
        var adblockFinalResult = 0L
        var systemFinalResult = 0L
        var sameLoadTimeCount = 0
        var totalNumberOfMeasuredUrls = 0
        for ((key, systemLoadTime) in systemResults) {
            // Check if entry exists in both maps and has valid value (value > 0)
            val adblockLoadTime = adblockResults[key]
            if (adblockLoadTime != null) {
                if (adblockLoadTime > 0 && systemLoadTime > 0) {
                    val diff = (adblockLoadTime - systemLoadTime) / SECONDS_IN_MS
                    if (MAX_DELTA_THRESHOLD_SECONDS > 0 && Math.abs(diff) > MAX_DELTA_THRESHOLD_SECONDS) {
                        Timber.d("Adblock is %s for `%s` of %d seconds, rejecting this result!",
                                if (diff > 0) "slower" else "faster", key, Math.abs(diff))
                    } else {
                        if (diff != 0L) {
                            Timber.d("Adblock is %s for `%s` of %d seconds",
                                    if (diff > 0) "slower" else "faster", key, Math.abs(diff))
                        } else {
                            ++sameLoadTimeCount
                        }
                        adblockFinalResult += adblockLoadTime / SECONDS_IN_MS
                        systemFinalResult += systemLoadTime / SECONDS_IN_MS
                        ++totalNumberOfMeasuredUrls
                    }
                } else {
                    Timber.w("Skipping url `%s` from measurement due to lack of value!", key)
                }
            } else {
                Timber.w("Skipping url `%s` from measurement as it was completed only in WebView!", key)
            }
        }
        Timber.d("compareResults() load time was equal for %d urls out of %d measured",
            sameLoadTimeCount, totalNumberOfMeasuredUrls)
        Timber.d("Adblock: compareResults() final pages load time is %s seconds", adblockFinalResult)
        Timber.d("System: compareResults() final pages load time is %s seconds", systemFinalResult)
        // We don't assert here just fail() to have a meaningful print already in Gitlab CI Web UI,
        // without the need to dive to a Test Object report.
        fail("adblockFinalResult = ${adblockFinalResult} seconds, " +
                "systemFinalResult = ${systemFinalResult} seconds, " +
                "delta = ${adblockFinalResult - systemFinalResult} seconds, normalized delta = " +
                "${(adblockFinalResult - systemFinalResult).toFloat() / systemFinalResult * 100}%, " +
                "urls counted = ${totalNumberOfMeasuredUrls} out of total ${urls.count()}, " +
                "urls which loads the same time = ${sameLoadTimeCount}")
    }

    @Throws(InterruptedException::class)
    private fun commonTestLogic() {
        var repetitionCount = 1
        while (repetitionCount-- > 0) {
            for (url in urls) {
                var fixedUrl = url;
                if (!fixedUrl.startsWith("http")) {
                    fixedUrl = "http://$url"
                }
                Timber.d("testLoadTime() loads %s", fixedUrl)
                val countDownLatch = CountDownLatch(1)
                val mainActivity = activityRule.getActivity()
                mainActivity.runOnUiThread {
                    webViewIdlingClient.setCountDownLatch(countDownLatch)
                    mainActivity.getWebViewForTesting().loadUrl(fixedUrl)
                }

                if (MAX_PAGE_LOAD_WAIT_TIME_SECONDS > 0) {
                    if (!countDownLatch.await(MAX_PAGE_LOAD_WAIT_TIME_SECONDS.toLong(), TimeUnit.SECONDS)) {
                        Timber.w("Skipping url `%s` from measurement due to too long loading time in %s!",
                                url, if (mainActivity.getWebViewForTesting() is AdblockWebView) "AdblockWebView" else "WebView")
                    }
                }
                else {
                    countDownLatch.await()
                }
                webViewIdlingClient.resetTimer()
            }
        }
    }

    companion object {
        // static data to keep state (results) between tests _1_ and _2_, then print it from test _3_
        private val adblockResults = mutableMapOf<String, Long>()
        private val systemResults = mutableMapOf<String, Long>()
    }
}