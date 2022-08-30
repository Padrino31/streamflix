package com.tanasi.sflix.fragments.tv_show

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanasi.sflix.models.People
import com.tanasi.sflix.models.TvShow
import com.tanasi.sflix.services.SflixService
import kotlinx.coroutines.launch

class TvShowViewModel : ViewModel() {

    private val sflixService = SflixService.build()

    private val _state = MutableLiveData<State>(State.Loading)
    val state: LiveData<State> = _state

    sealed class State {
        object Loading : State()

        data class SuccessLoading(val tvShow: TvShow) : State()
        data class FailedLoading(val error: Exception) : State()
    }


    fun fetchTvShow(id: String) = viewModelScope.launch {
        _state.value = State.Loading

        _state.value = try {
            val document = sflixService.fetchTvShow(id)

            var released = ""
            var runtime: Int? = null
            val casts = mutableListOf<People>()

            document.select("div.elements > .row > div > .row-line").forEach { element ->
                val type = element?.select(".type")?.text() ?: return@forEach
                when {
                    type.contains("Released") -> released = element.ownText().trim()
                    type.contains("Duration") -> runtime = element.ownText()
                        .removeSuffix("min")
                        .trim()
                        .toIntOrNull()
                    type.contains("Casts") -> casts.addAll(
                        element.select("a").map {
                            People(
                                slug = it.attr("href").substringAfter("/cast/"),
                                name = it.text(),
                            )
                        }
                    )
                }
            }

            State.SuccessLoading(
                TvShow(
                    id = id,
                    title = document.selectFirst("h2.heading-name")?.text() ?: "",
                    overview = document.selectFirst("div.description")?.ownText() ?: "",
                    released = released,
                    runtime = runtime,
                    youtubeTrailerId = document.selectFirst("iframe#iframe-trailer")
                        ?.attr("data-src")
                        ?.substringAfterLast("/"),
                    quality = document.selectFirst(".fs-item > .quality")?.text()
                        ?.trim() ?: "",
                    rating = document.selectFirst(".fs-item > .imdb")?.text()
                        ?.trim()
                        ?.removePrefix("IMDB:")
                        ?.toDoubleOrNull(),
                    poster = document.selectFirst("div.detail_page-watch img.film-poster-img")
                        ?.attr("src"),
                    banner = document.selectFirst("div.detail-container > div.cover_follow")
                        ?.attr("style")
                        ?.substringAfter("background-image: url(")
                        ?.substringBefore(");"),

                    casts = casts,
                )
            )
        } catch (e: Exception) {
            State.FailedLoading(e)
        }
    }
}