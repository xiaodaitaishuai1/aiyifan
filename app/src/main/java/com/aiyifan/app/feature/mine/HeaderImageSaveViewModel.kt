package com.aiyifan.app.feature.mine

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class HeaderImageSaveViewModel : ViewModel() {
    enum class State { READY, SAVING, SAVED, FAILED }
    private val currentState = MutableLiveData(State.READY)
    val state: LiveData<State> = currentState
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun save(context: Context, name: String) {
        if (currentState.value == State.SAVING) return
        currentState.value = State.SAVING
        val application = context.applicationContext
        saveScope.launch {
            currentState.value = try {
                // Finish this bounded file transaction even if the viewer closes mid-copy.
                // Only application context is retained; rotation reuses this same operation.
                withContext(Dispatchers.IO + NonCancellable) { HeaderImageSaver.save(application, name) }
                State.SAVED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                State.FAILED
            }
        }
    }

    override fun onCleared() {
        saveScope.cancel()
        super.onCleared()
    }

    fun consumeResult() {
        if (currentState.value == State.SAVED || currentState.value == State.FAILED) currentState.value = State.READY
    }
}
