package com.aiyifan.app.feature.localmedia

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aiyifan.app.core.ui.applySystemBarsPadding
import com.aiyifan.app.core.ui.setupEdgeToEdge
import com.aiyifan.app.databinding.ActivityLocalMediaBinding

class LocalMediaActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        val binding = ActivityLocalMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding(left = true, top = true, right = true, bottom = true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.localMediaContainer.id, LocalMediaFragment())
                .commit()
        }
    }
}
