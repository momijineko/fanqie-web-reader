package com.momijineko.fanqie.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.momijineko.fanqie.R
import com.momijineko.fanqie.databinding.ActivityMainBinding
import com.momijineko.fanqie.util.EinkUtils

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EinkUtils.applyEinkOptimizations(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val nav = binding.bottomNav
        nav.setOnItemSelectedListener { item ->
            val frag: Fragment = when (item.itemId) {
                R.id.nav_search -> SearchFragment()
                R.id.nav_shelf -> ShelfFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> SearchFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, frag)
                .commit()
            true
        }
        if (savedInstanceState == null) nav.selectedItemId = R.id.nav_search
    }
}
