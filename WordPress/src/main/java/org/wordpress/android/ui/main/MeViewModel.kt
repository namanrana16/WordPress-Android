package org.wordpress.android.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.wordpress.android.BuildConfig
import org.wordpress.android.R
import org.wordpress.android.WordPress
import org.wordpress.android.models.recommend.RecommendApiCallsProvider
import org.wordpress.android.models.recommend.RecommendApiCallsProvider.RecommendAppName
import org.wordpress.android.models.recommend.RecommendApiCallsProvider.RecommendCallResult
import org.wordpress.android.models.recommend.RecommendApiCallsProvider.RecommendCallResult.Failure
import org.wordpress.android.models.recommend.RecommendApiCallsProvider.RecommendCallResult.Success
import org.wordpress.android.modules.BG_THREAD
import org.wordpress.android.modules.UI_THREAD
import org.wordpress.android.ui.mysite.SelectedSiteRepository
import org.wordpress.android.ui.recommend.RecommendAppState
import org.wordpress.android.ui.recommend.RecommendAppState.ApiFetchedResult
import org.wordpress.android.ui.recommend.RecommendAppState.FetchingApi
import org.wordpress.android.util.NetworkUtilsWrapper
import org.wordpress.android.viewmodel.ContextProvider
import org.wordpress.android.viewmodel.Event
import org.wordpress.android.viewmodel.ScopedViewModel
import javax.inject.Inject
import javax.inject.Named

class MeViewModel
@Inject constructor(
    @Named(UI_THREAD) mainDispatcher: CoroutineDispatcher,
    @Named(BG_THREAD) val bgDispatcher: CoroutineDispatcher,
    private val selectedSiteRepository: SelectedSiteRepository,
    private val recommendApiCallsProvider: RecommendApiCallsProvider,
    private val networkUtilsWrapper: NetworkUtilsWrapper,
    private val contextProvider: ContextProvider
) : ScopedViewModel(mainDispatcher) {
    private val _showDisconnectDialog = MutableLiveData<Event<Boolean>>()
    val showDisconnectDialog: LiveData<Event<Boolean>> = _showDisconnectDialog

    private val _recommendUiState = MutableLiveData<RecommendAppState>()
    val recommendUiState: LiveData<Event<RecommendAppUiState>> = _recommendUiState.map { it.toUiState() }

    data class RecommendAppUiState(
        val showLoading: Boolean = false,
        val error: String? = null,
        val message: String,
        val link: String
    ) {
        constructor(showLoading: Boolean) : this(
                showLoading = showLoading,
                message = "",
                link = ""
        )
        constructor(error: String) : this(
                error = error,
                message = "",
                link = ""
        )
        fun isError() = error != null
    }

    fun signOutWordPress(application: WordPress) {
        launch {
            _showDisconnectDialog.value = Event(true)
            withContext(bgDispatcher) {
                application.wordPressComSignOut()
            }
            _showDisconnectDialog.value = Event(false)
        }
    }

    fun openDisconnectDialog() {
        _showDisconnectDialog.value = Event(true)
    }

    fun getSite() = selectedSiteRepository.getSelectedSite()

    fun onRecommendTheApp() {
        when (val state = _recommendUiState.value) {
            is ApiFetchedResult -> {
                if (state.isError()) {
                    getRecommendTemplate()
                } else {
                    _recommendUiState.value = state
                }
            }
            FetchingApi -> {
                return
            }
            null -> {
                getRecommendTemplate()
            }
        }
    }

    private fun getRecommendTemplate() {
        launch {
            _recommendUiState.value = FetchingApi
            withContext(bgDispatcher) {
                delay(SHOW_LOADING_DELAY)

                if (!networkUtilsWrapper.isNetworkAvailable()) {
                    _recommendUiState.postValue(
                            ApiFetchedResult(contextProvider.getContext().getString(R.string.no_network_message))
                    )
                } else {
                    val fetchedResult = recommendApiCallsProvider.getRecommendTemplate(
                            if (BuildConfig.IS_JETPACK_APP) {
                                RecommendAppName.Jetpack.appName
                            } else {
                                RecommendAppName.WordPress.appName
                            }
                    ).toFetchedResult()

                    _recommendUiState.postValue(fetchedResult)
                }
            }
        }
    }

    private fun RecommendCallResult.toFetchedResult(): ApiFetchedResult {
        return when (this) {
            is Failure -> ApiFetchedResult(error = this.error)
            is Success -> ApiFetchedResult(
                    message = this.templateData.message,
                    link = this.templateData.link
            )
        }
    }

    private fun RecommendAppState.toUiState(): Event<RecommendAppUiState> {
        return Event(when (this) {
            is ApiFetchedResult -> if (this.isError()) {
                RecommendAppUiState(this.error!!)
            } else {
                RecommendAppUiState(
                        link = this.link,
                        message = this.message
                )
            }
            FetchingApi -> RecommendAppUiState(showLoading = true)
        })
    }

    companion object {
        private const val SHOW_LOADING_DELAY = 300L
    }
}
