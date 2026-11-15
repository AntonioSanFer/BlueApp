package com.blueapp.detox

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView

data class AppEntry(val pkg: String, val label: String, val icon: Drawable, val recommended: Boolean)

/** Launchable apps, recommended ones first. Loads off the main thread; shown in a ListView. */
class AppListAdapter(private val ctx: Context, private val selected: MutableSet<String>,
                     private val onChange: (Set<String>) -> Unit) : BaseAdapter() {

    private var items: List<AppEntry> = emptyList()

    fun load(list: ListView, done: (List<AppEntry>) -> Unit = {}) {
        Thread {
            val pm = ctx.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != ctx.packageName }
                .mapNotNull { pkg ->
                    runCatching {
                        val ai = pm.getApplicationInfo(pkg, 0)
                        AppEntry(pkg, ai.loadLabel(pm).toString(), ai.loadIcon(pm), pkg in Prefs.RECOMMENDED)
                    }.getOrNull()
                }
                .sortedWith(compareByDescending<AppEntry> { it.recommended }
                    .thenByDescending { it.pkg in selected }
                    .thenBy { it.label.lowercase() })
            Handler(Looper.getMainLooper()).post {
                items = apps
                notifyDataSetChanged()
                done(apps)
            }
        }.start()
        list.adapter = this
        list.setOnItemClickListener { _, _, pos, _ -> toggle(items[pos].pkg) }
    }

    private fun toggle(pkg: String) {
        if (!selected.remove(pkg)) selected.add(pkg)
        notifyDataSetChanged()
        onChange(selected)
    }

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: LayoutInflater.from(ctx).inflate(R.layout.row_app, parent, false)
        val e = items[position]
        v.findViewById<ImageView>(R.id.icon).setImageDrawable(e.icon)
        v.findViewById<TextView>(R.id.label).text = e.label
        v.findViewById<TextView>(R.id.badge).visibility = if (e.recommended) View.VISIBLE else View.GONE
        v.findViewById<CheckBox>(R.id.check).isChecked = e.pkg in selected
        return v
    }
}
