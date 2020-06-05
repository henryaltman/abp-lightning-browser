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
package acr.browser.lightning.settings.activity


import android.R
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.adblockplus.libadblockplus.android.AdblockEngine
import org.adblockplus.libadblockplus.android.settings.*
import timber.log.Timber

class AdblockPlusSettingsActivity : AppCompatActivity()
        ,BaseSettingsFragment.Provider
        ,GeneralSettingsFragment.Listener
        ,WhitelistedDomainsSettingsFragment.Listener {

    override fun onCreate(savedInstanceState: Bundle?) {
        // retaining AdblockEngine asynchronously
        AdblockHelper.get().provider.retain(true)
        super.onCreate(savedInstanceState)
        insertGeneralFragment()
    }

    private fun insertGeneralFragment() {
        supportFragmentManager
                .beginTransaction()
                .replace(
                        R.id.content,
                        GeneralSettingsFragment.newInstance())
                .commit()
    }

    private fun insertWhitelistedFragment() {
        supportFragmentManager
                .beginTransaction()
                .replace(
                        R.id.content,
                        WhitelistedDomainsSettingsFragment.newInstance())
                .addToBackStack(WhitelistedDomainsSettingsFragment::class.java.simpleName)
                .commit()
    }

    // provider
    override fun getAdblockEngine(): AdblockEngine {
        AdblockHelper.get().provider.waitForReady()
        return AdblockHelper.get().provider.engine
    }

    override fun getAdblockSettingsStorage(): AdblockSettingsStorage {
        return AdblockHelper.get().storage
    }

    // listener
    override fun onAdblockSettingsChanged(fragment: BaseSettingsFragment<*>) {
        Timber.d("AdblockHelper setting changed:\n%s", fragment.settings.toString())
    }

    override fun onWhitelistedDomainsClicked(fragment: GeneralSettingsFragment) {
        insertWhitelistedFragment()
    }

    override fun isValidDomain(fragment: WhitelistedDomainsSettingsFragment,
                               domain: String,
                               settings: AdblockSettings): Boolean {
        // show error here if domain is invalid
        return !domain.isNullOrEmpty()
    }

    override fun onDestroy() {
        super.onDestroy()
        AdblockHelper.get().provider.release()
    }
}