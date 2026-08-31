/*
 * Copyright 2026, OpenRemote Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package io.openremote.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.openremote.app.databinding.ActivitySplashBinding
import io.openremote.app.model.ProjectItem
import io.openremote.app.util.Constants
import io.openremote.orlib.ORConstants
import io.openremote.orlib.ui.OrMainActivity
import io.openremote.orlib.ui.OrPrivacyPolicyActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : Activity() {

  object SplashActivityConstants {
    const val SHOW_CONDITIONS_REQUEST = 34563
  }

  private lateinit var binding: ActivitySplashBinding
  private lateinit var sharedPreferences: SharedPreferences
  private lateinit var projectItems: MutableList<ProjectItem>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivitySplashBinding.inflate(layoutInflater)
    val view = binding.root
    setContentView(view)

    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    projectItems =
      sharedPreferences.getString(Constants.PROJECT_LIST, null)?.let {
        jacksonObjectMapper().readValue<List<ProjectItem>>(it).toMutableList()
      } ?: mutableListOf()

    intent.extras?.apply {
      this.getString(ORConstants.CLEAR_URL)?.apply {
        projectItems.removeIf { it.url == this }
        sharedPreferences
          .edit()
          .putString(Constants.PROJECT_LIST, jacksonObjectMapper().writeValueAsString(projectItems))
          .apply()
      }
    }

    val privacyPolicyAccepted = sharedPreferences.getBoolean("privacyPolicyAccepted", false)

    if (!privacyPolicyAccepted) {
      val intent = Intent(this@SplashActivity, OrPrivacyPolicyActivity::class.java)
      startActivityForResult(intent, SplashActivityConstants.SHOW_CONDITIONS_REQUEST)
    } else {
      startNextActivity()
    }
  }

  private fun startNextActivity() {
    when {
      projectItems.size == 1 -> {
        val intent = Intent(this, OrMainActivity::class.java)
        intent.putExtra(ORConstants.BASE_URL_KEY, projectItems[0].url)
        startActivity(intent)
        finish()
      }
      projectItems.size > 1 -> {
        startActivity(Intent(this@SplashActivity, ProjectListActivity::class.java))
        finish()
      }
      else -> {
        startActivity(Intent(this@SplashActivity, ProjectWizardActivity::class.java))
        finish()
      }
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == SplashActivityConstants.SHOW_CONDITIONS_REQUEST) {
      if (resultCode == RESULT_OK) {
        sharedPreferences.edit().putBoolean("privacyPolicyAccepted", true).apply()
        startNextActivity()
      }
    }
    super.onActivityResult(requestCode, resultCode, data)
  }
}
