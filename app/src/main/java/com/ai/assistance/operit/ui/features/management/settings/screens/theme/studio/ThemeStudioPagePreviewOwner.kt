package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio


import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/** 预览使用自己的状态注册表和 ViewModelStore，关闭后取消该预览中的查询与观察。 */
internal class StudioPagePreviewOwner(application: Application) :
    ViewModelStoreOwner, SavedStateRegistryOwner, HasDefaultViewModelProviderFactory, OnBackPressedDispatcherOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val onBackPressedDispatcher = OnBackPressedDispatcher()
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = SavedStateViewModelFactory(application, this)
    override val defaultViewModelCreationExtras: CreationExtras = MutableCreationExtras().apply {
        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] = application
        this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@StudioPagePreviewOwner
        this[VIEW_MODEL_STORE_OWNER_KEY] = this@StudioPagePreviewOwner
    }

    init {
        savedState.performAttach()
        enableSavedStateHandles()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun moveTo(state: Lifecycle.State) {
        // 状态注册表已进入 CREATED，不能再退回 INITIALIZED。
        if (state != Lifecycle.State.INITIALIZED && registry.currentState != Lifecycle.State.DESTROYED) {
            registry.currentState = state
        }
    }

    fun close() {
        moveTo(Lifecycle.State.DESTROYED)
        viewModelStore.clear()
    }
}
