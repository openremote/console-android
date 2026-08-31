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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.openremote.app.databinding.RowProjectItemBinding
import io.openremote.app.model.ProjectItem

class ProjectListAdapter(
  val items: MutableList<ProjectItem>,
  private val goToMainActivity: (url: String) -> (Unit),
) : RecyclerView.Adapter<ProjectListAdapter.ViewHolder>() {

  // create new views
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    val binding = RowProjectItemBinding.inflate(inflater, parent, false)
    return ViewHolder(binding, goToMainActivity)
  }

  // binds the list items to a view
  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bindProjectItem(items[position])
  }

  // return the number of the items in the list
  override fun getItemCount(): Int {
    return items.size
  }

  fun remove(position: Int) {
    this.items.removeAt(position)
    this.notifyDataSetChanged()
  }

  // Holds the views for adding it to image and text
  class ViewHolder(
    private val binding: RowProjectItemBinding,
    private val goToMainActivity: (url: String) -> (Unit),
  ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

    lateinit var url: String

    init {
      binding.root.setOnClickListener(this)
    }

    fun bindProjectItem(projectItem: ProjectItem) {
      this.url = projectItem.url
      binding.host.text = projectItem.host
      projectItem.app.run {
        binding.app.visibility = View.VISIBLE
        binding.app.text = this
      }
      projectItem.realm?.run {
        binding.realm.visibility = View.VISIBLE
        binding.realm.text = this
      }
    }

    override fun onClick(view: View?) {
      goToMainActivity(url)
    }
  }
}
