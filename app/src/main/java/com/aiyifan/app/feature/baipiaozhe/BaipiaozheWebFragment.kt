package com.aiyifan.app.feature.baipiaozhe

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebStorage
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.aiyifan.app.R
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.data.baipiaozhe.PlaybackHttpHeaders
import com.aiyifan.app.databinding.FragmentBaipiaozheBinding
import com.aiyifan.app.feature.video.VideoPlayerActivity
import java.net.URI

/**
 * Embedded baipiaozhe Tab: hosts the official site in a WebView so login, search
 * and feature browsing run inside the site. A small injected hook intercepts the
 * resolved playback URL and hands it to the native player via [BaipiaozhePlaybackBridge].
 */
class BaipiaozheWebFragment : Fragment() {
    private var _binding: FragmentBaipiaozheBinding? = null
    private val binding get() = _binding!!
    private val directPlaybackLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == VideoPlayerActivity.RESULT_DIRECT_PLAYBACK_FAILED) {
            showUnavailable()
            resumeWebPlayback()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBaipiaozheBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureWebView()
        binding.baipiaozheRetryButton.setOnClickListener { reload() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val webView = binding.baipiaozheWebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(webView, true)
            }
        }
        WebStorage.getInstance()
        webView.addJavascriptInterface(
            BaipiaozhePlaybackBridge(
                onUnavailable = { showUnavailable() },
                dispatchToMain = { action -> webView.post { action() } },
                isTrustedPage = { isTrustedBaipiaozheUrl(webView.url) },
                headersForPlaybackUrl = ::currentHeaders,
                onPlaybackReady = { mediaUrl, title ->
                    directPlaybackLauncher.launch(VideoPlayerActivity.directIntent(requireContext(), mediaUrl, title))
                },
            ),
            JS_BRIDGE_NAME,
        )
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (isTrustedBaipiaozheUrl(url)) injectPlaybackHook(view)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    binding.baipiaozheErrorView.isVisible = true
                }
                super.onReceivedError(view, request, error)
            }
        }
        webView.loadUrl(BASE_URL)
    }

    private fun injectPlaybackHook(view: WebView) {
        view.evaluateJavascript(PLAYBACK_HOOK_SCRIPT, null)
    }

    private fun currentHeaders(mediaUrl: String): PlaybackHttpHeaders = PlaybackHttpHeaders(
        referer = binding.baipiaozheWebView.url?.takeIf(::isTrustedBaipiaozheUrl) ?: BASE_URL,
        userAgent = binding.baipiaozheWebView.settings.userAgentString,
        cookie = CookieManager.getInstance().getCookie(mediaUrl),
    )

    private fun isTrustedBaipiaozheUrl(url: String?): Boolean {
        val host = url?.let { runCatching { URI(it).host }.getOrNull() } ?: return false
        return host == BAIPIAOZHE_HOST || host.endsWith(".$BAIPIAOZHE_HOST")
    }

    private fun showUnavailable() {
        binding.root.post {
            android.widget.Toast.makeText(requireContext(), R.string.baipiaozhe_unavailable, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun reload() {
        binding.baipiaozheErrorView.isVisible = false
        binding.baipiaozheWebView.reload()
    }

    private fun resumeWebPlayback() {
        binding.baipiaozheWebView.evaluateJavascript(
            "window.AiyifanPlayback && window.AiyifanPlayback.resumeLastVideo && window.AiyifanPlayback.resumeLastVideo();",
            null,
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val BASE_URL = "https://baipiaozhe.ai/"
        const val BAIPIAOZHE_HOST = "baipiaozhe.ai"
        const val JS_BRIDGE_NAME = "AiyifanBridge"

        const val PLAYBACK_HOOK_SCRIPT = """
            (function () {
              if (window.__aiyifanPlaybackHookInstalled) return;
              window.__aiyifanPlaybackHookInstalled = true;
              var notified = false;
              var lastVideo = null;
              function postPlayback(payload) {
                if (notified) return;
                notified = true;
                if (window.AiyifanBridge && window.AiyifanBridge.onPlaybackResolved) {
                  window.AiyifanBridge.onPlaybackResolved(
                    typeof payload === 'string' ? payload : JSON.stringify(payload)
                  );
                }
              }
              window.AiyifanPlayback = {
                postPlayback: postPlayback,
                resumeLastVideo: function () {
                  if (lastVideo) lastVideo.play().catch(function () {});
                }
              };
              function findPlaybackUrl(value, depth) {
                if (depth > 5 || value == null) return null;
                if (typeof value === 'string') {
                  if (/\\.(m3u8|mp4)([?#]|$)/i.test(value)) return value;
                  try { return findPlaybackUrl(JSON.parse(value), depth + 1); } catch (_) { return null; }
                }
                if (typeof value !== 'object') return null;
                var direct = value.play_url || value.playUrl || value.url || value.src;
                if (direct) return findPlaybackUrl(direct, depth + 1);
                for (var key in value) {
                  if (Object.prototype.hasOwnProperty.call(value, key)) {
                    var nested = findPlaybackUrl(value[key], depth + 1);
                    if (nested) return nested;
                  }
                }
                return null;
              }
              function capture(value) {
                var url = findPlaybackUrl(value, 0);
                if (url) postPlayback(url);
              }
              function hookMethod(bridge, method) {
                var original = bridge && bridge[method];
                if (typeof original !== 'function' || original.__aiyifanWrapped) return;
                function wrapped() {
                  capture(Array.prototype.slice.call(arguments));
                  return original.apply(this, arguments);
                }
                wrapped.__aiyifanWrapped = true;
                bridge[method] = wrapped;
              }
              function hookOfficialBridges() {
                [window.H5ShellNativeBridge, window.AiMovieAndroidBridge].forEach(function (bridge) {
                  ['post', 'postMessage', 'send', 'play', 'playVideo', 'openPlayer'].forEach(function (method) {
                    hookMethod(bridge, method);
                  });
                });
              }
              document.addEventListener('play', function (event) {
                var video = event.target;
                if (!video || video.tagName !== 'VIDEO' || notified) return;
                lastVideo = video;
                var url = video.currentSrc || video.src;
                if (url) {
                  video.pause();
                  postPlayback(url);
                }
              }, true);
              hookOfficialBridges();
              window.setInterval(hookOfficialBridges, 500);
            })();
        """
    }
}
