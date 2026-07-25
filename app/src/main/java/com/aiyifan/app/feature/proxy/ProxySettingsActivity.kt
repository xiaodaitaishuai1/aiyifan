package com.aiyifan.app.feature.proxy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.ui.applySystemBarsPadding
import com.aiyifan.app.core.ui.setupEdgeToEdge
import com.aiyifan.app.databinding.ActivityProxySettingsBinding
import com.aiyifan.app.feature.proxy.domain.ProxyConnectionState
import com.aiyifan.app.feature.proxy.domain.ProxyNode
import com.aiyifan.app.feature.proxy.domain.SubscriptionImportError
import com.aiyifan.app.feature.proxy.domain.SubscriptionImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProxySettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProxySettingsBinding
    private val proxyManager get() = AppGraph.proxyManager
    private var isRenderingNodes = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        binding = ActivityProxySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding(left = true, right = true, bottom = true)
        binding.topBar.applySystemBarsPadding(top = true, growHeight = true)

        binding.backButton.setOnClickListener { finish() }
        binding.updateSubscriptionButton.setOnClickListener { updateSubscription() }
        binding.connectButton.setOnClickListener { connect() }
        binding.disconnectButton.setOnClickListener {
            proxyManager.disconnect()
            renderStatus()
        }
        binding.nodeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isRenderingNodes) {
                    proxyManager.nodes.getOrNull(position)?.let { proxyManager.select(it.id) }
                    renderStatus()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.subscriptionEdit.setText(proxyManager.storedSubscriptionUrl().orEmpty())
        renderNodes()
        renderStatus()
        restoreSavedSubscription()
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
    }

    private fun updateSubscription() {
        val subscriptionUrl = binding.subscriptionEdit.text?.toString()?.trim().orEmpty()
        if (subscriptionUrl.isEmpty()) {
            binding.statusView.text = "\u8BF7\u8F93\u5165\u8BA2\u9605\u5730\u5740"
            return
        }

        setLoading(true)
        binding.statusView.text = "\u6B63\u5728\u66F4\u65B0\u8BA2\u9605"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { proxyManager.refresh(subscriptionUrl) }
            }
            setLoading(false)
            result.fold(
                onSuccess = ::renderImportResult,
                onFailure = { binding.statusView.text = "\u8BA2\u9605\u66F4\u65B0\u5931\u8D25\uFF0C\u8BF7\u68C0\u67E5\u5730\u5740\u548C\u7F51\u7EDC" },
            )
        }
    }

    private fun restoreSavedSubscription() {
        if (!ProxySubscriptionRestorePolicy.shouldRestore(proxyManager.nodes.size, proxyManager.activeEndpoint)) return
        val subscriptionUrl = proxyManager.storedSubscriptionUrl()?.trim().orEmpty()
        if (subscriptionUrl.isEmpty()) return

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { proxyManager.refresh(subscriptionUrl) } }
            result.getOrNull()?.let(::renderImportResult)
        }
    }

    private fun connect() {
        if (proxyManager.selectedNode == null) {
            binding.statusView.text = "\u8BF7\u5148\u66F4\u65B0\u8BA2\u9605\u5E76\u9009\u62E9\u8282\u70B9"
            return
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        setLoading(true)
        binding.statusView.text = "\u6B63\u5728\u8FDE\u63A5"
        lifecycleScope.launch {
            val endpoint = withContext(Dispatchers.IO) { proxyManager.connect() }
            setLoading(false)
            if (endpoint == null) {
                binding.statusView.text = when (proxyManager.lastConnectionFailure) {
                    ProxyConnectionFailure.CONFIGURATION -> "\u8282\u70B9\u914D\u7F6E\u4E0D\u517C\u5BB9\uFF0C\u8BF7\u66F4\u6362\u8282\u70B9"
                    ProxyConnectionFailure.SERVICE_CREATION -> "\u4EE3\u7406\u670D\u52A1\u521B\u5EFA\u5931\u8D25\uFF0C\u8BF7\u91CD\u8BD5"
                    ProxyConnectionFailure.SERVICE_START -> "\u4EE3\u7406\u670D\u52A1\u542F\u52A8\u5931\u8D25\uFF0C\u8BF7\u91CD\u8BD5"
                    else -> "\u8FDE\u63A5\u5931\u8D25\uFF0C\u8BF7\u66F4\u6362\u8282\u70B9\u540E\u91CD\u8BD5"
                }
            } else {
                renderStatus()
            }
        }
    }

    private fun renderImportResult(result: SubscriptionImportResult) {
        when (result) {
            is SubscriptionImportResult.Imported -> {
                renderNodes()
                binding.statusView.text = "\u5DF2\u5BFC\u5165 ${result.nodes.size} \u4E2A\u8282\u70B9"
            }

            is SubscriptionImportResult.Rejected -> {
                binding.statusView.text = when (result.error) {
                    SubscriptionImportError.EMPTY_SUBSCRIPTION -> "\u8BA2\u9605\u5185\u5BB9\u4E3A\u7A7A"
                    SubscriptionImportError.INVALID_BASE64 -> "\u8BA2\u9605\u5185\u5BB9\u683C\u5F0F\u65E0\u6548"
                    SubscriptionImportError.NO_SUPPORTED_NODES -> "\u672A\u53D1\u73B0\u53EF\u7528\u8282\u70B9"
                }
            }
        }
    }

    private fun renderNodes() {
        isRenderingNodes = true
        val nodes = proxyManager.nodes
        binding.nodeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            nodes.map(::nodeLabel),
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.nodeSpinner.isEnabled = nodes.isNotEmpty()
        nodes.indexOfFirst { it.id == proxyManager.selectedNode?.id }
            .takeIf { it >= 0 }
            ?.let(binding.nodeSpinner::setSelection)
        binding.emptyNodesView.visibility = if (nodes.isEmpty()) View.VISIBLE else View.GONE
        isRenderingNodes = false
    }

    private fun renderStatus() {
        val hasNode = proxyManager.selectedNode != null
        binding.connectButton.isEnabled = hasNode && proxyManager.state !is ProxyConnectionState.Connected
        binding.disconnectButton.isEnabled = proxyManager.state !is ProxyConnectionState.Disconnected
        binding.statusView.text = when (val state = proxyManager.state) {
            ProxyConnectionState.Disconnected -> if (hasNode) "\u5DF2\u65AD\u5F00" else "\u5C1A\u672A\u9009\u62E9\u8282\u70B9"
            is ProxyConnectionState.Connecting -> "\u6B63\u5728\u8FDE\u63A5 ${nodeLabel(state.node)}"
            is ProxyConnectionState.Connected -> {
                val endpoint = proxyManager.activeEndpoint
                if (endpoint == null) {
                    "\u8FDE\u63A5\u72B6\u6001\u5DF2\u5931\u6548"
                } else {
                    "\u5DF2\u8FDE\u63A5 ${nodeLabel(state.node)}"
                }
            }
            ProxyConnectionState.Failed -> "\u8FDE\u63A5\u5931\u8D25"
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.updateSubscriptionButton.isEnabled = !isLoading
        binding.connectButton.isEnabled = !isLoading && proxyManager.selectedNode != null
        binding.disconnectButton.isEnabled = !isLoading && proxyManager.state !is ProxyConnectionState.Disconnected
        binding.nodeSpinner.isEnabled = !isLoading && proxyManager.nodes.isNotEmpty()
    }

    private fun nodeLabel(node: ProxyNode): String = "${node.displayName} (${node.protocol.name})"

    private companion object {
        const val REQUEST_NOTIFICATIONS = 4102
    }
}
