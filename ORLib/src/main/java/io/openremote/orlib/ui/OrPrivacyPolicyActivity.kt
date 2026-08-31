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
package io.openremote.orlib.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import io.openremote.orlib.databinding.ActivityOrPrivacyPolicyBinding

class OrPrivacyPolicyActivity : AppCompatActivity() {

  private lateinit var binding: ActivityOrPrivacyPolicyBinding
  private var isAccepted = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityOrPrivacyPolicyBinding.inflate(layoutInflater)
    val view = binding.root
    setContentView(view)

    binding.webView.loadUrl("https://openremote.io/privacy-policy/")

    binding.buttonAccept.setOnClickListener {
      if (isAccepted) {
        val intent = Intent()
        setResult(RESULT_OK, intent)
        finish()
      }
    }

    binding.checkbox.setOnClickListener(
      View.OnClickListener { v: View? ->
        isAccepted = !isAccepted

        binding.buttonAccept.setEnabled(isAccepted)
      }
    )
  }
}
