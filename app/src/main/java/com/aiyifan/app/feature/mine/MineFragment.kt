package com.aiyifan.app.feature.mine

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aiyifan.app.R
import com.aiyifan.app.core.ui.ThemeMode
import com.aiyifan.app.core.ui.ThemePreferenceStore
import com.aiyifan.app.databinding.FragmentMineBinding
import com.aiyifan.app.feature.auth.LoginActivity
import com.aiyifan.app.feature.collection.CollectionActivity
import com.aiyifan.app.feature.history.HistoryActivity
import com.aiyifan.app.feature.proxy.ProxySettingsActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MineFragment : Fragment() {
    private var _binding: FragmentMineBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val themePreferenceStore = ThemePreferenceStore(requireContext())
        binding.themeSettingsSummary.setText(themePreferenceStore.currentMode().labelRes)
        binding.loginButton.setOnClickListener { startActivity(Intent(requireContext(), LoginActivity::class.java)) }
        binding.historyButton.setOnClickListener { startActivity(Intent(requireContext(), HistoryActivity::class.java)) }
        binding.collectionButton.setOnClickListener { startActivity(Intent(requireContext(), CollectionActivity::class.java)) }
        binding.proxySettingsButton.setOnClickListener {
            startActivity(Intent(requireContext(), ProxySettingsActivity::class.java))
        }
        binding.themeSettingsButton.setOnClickListener {
            val modes = ThemeMode.entries
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.theme_settings_title)
                .setSingleChoiceItems(
                    modes.map { getString(it.labelRes) }.toTypedArray(),
                    modes.indexOf(themePreferenceStore.currentMode()),
                ) { dialog, which ->
                    dialog.dismiss()
                    themePreferenceStore.select(modes[which])
                }
                .show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private val ThemeMode.labelRes: Int
        get() = when (this) {
            ThemeMode.SYSTEM -> R.string.theme_mode_system
            ThemeMode.LIGHT -> R.string.theme_mode_light
            ThemeMode.DARK -> R.string.theme_mode_dark
        }
}
