package org.wordpress.android.viewmodel.giphy.provider

import android.content.Context
import android.net.Uri
import com.tenor.android.core.constant.AspectRatioRange
import com.tenor.android.core.constant.MediaCollectionFormat
import com.tenor.android.core.constant.MediaFilter
import com.tenor.android.core.model.impl.Result
import com.tenor.android.core.network.ApiClient
import com.tenor.android.core.network.IApiClient
import com.tenor.android.core.response.WeakRefCallback
import com.tenor.android.core.response.impl.GifsResponse
import org.wordpress.android.R.string
import org.wordpress.android.viewmodel.giphy.GiphyMediaViewModel
import org.wordpress.android.viewmodel.giphy.MutableGiphyMediaViewModel
import org.wordpress.android.viewmodel.giphy.provider.GifProvider.GifRequestFailedException

/**
 * Implementation of a GifProvider using the Tenor GIF API as provider
 *
 * This Provider performs requests to the Tenor API using the [tenorClient].
 */

internal class TenorProvider @JvmOverloads constructor(
    val context: Context,
    private val tenorClient: IApiClient
) : GifProvider {
    /**
     * Implementation of the [GifProvider] search method, it will call the Tenor client search
     * right away with the provided parameters.
     */
    override fun search(
        query: String,
        position: Int,
        loadSize: Int?,
        onSuccess: (List<GiphyMediaViewModel>, Int?) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        tenorClient.simpleSearch(
                query,
                position.toString(),
                loadSize,
                onSuccess = { response ->
                    handleResponse(response, onSuccess)
                },
                onFailure = {
                    it?.let { throwable -> handleFailure(throwable, onFailure) }
                }
        )
    }

    /**
     * This method act as a simplification to call a search within the Tenor API and the callback
     * creation
     * [MediaFilter] must be BASIC or the returned Media will not have a displayable thumbnail
     * All other provided parameters are set following the Tenor API Documentation
     *
     * The [onFailure] will be called assuming that no valid GIF was found
     * or that a direct failure was found by Tenor API
     *
     * Method is inlined for better high-order functions performance
     */
    private inline fun IApiClient.simpleSearch(
        query: String,
        position: String,
        loadSize: Int?,
        crossinline onSuccess: (GifsResponse) -> Unit,
        crossinline onFailure: (Throwable?) -> Unit
    ) {
        search(
                ApiClient.getServiceIds(context),
                query,
                loadSize.fittedToMaximumAllowed,
                position,
                MediaFilter.BASIC,
                AspectRatioRange.ALL

        ).enqueue(object : WeakRefCallback<Context, GifsResponse>(context) {
            override fun success(context: Context, response: GifsResponse?) {
                response?.let(onSuccess)
                        ?: onFailure(
                                GifRequestFailedException(
                                        context.getString(string.giphy_picker_empty_search_list)
                                )
                        )
            }

            override fun failure(context: Context, throwable: Throwable?) {
                onFailure(throwable)
            }
        })
    }

    /**
     * If the search request succeeds an [List] of [MutableGiphyMediaViewModel] will be passed with the next position
     * for pagination. If there's no next position provided, it will be passed as null.
     *
     * Method is inlined for better high-order functions performance
     */
    private inline fun handleResponse(
        response: GifsResponse,
        onSuccess: (List<GiphyMediaViewModel>, Int?) -> Unit
    ) {
        response.results.map { it.toMutableGifMediaViewModel() }
                .let { gifs ->
                    val nextPosition = response.next.toIntOrNull()
                    onSuccess(gifs, nextPosition)
                }
    }

    /**
     * If the search request fails an [GifRequestFailedException] will be passed with the API
     * message. If there's no message provided, a generic message will be applied.
     *
     * Method is inlined for better high-order functions performance
     */
    private inline fun handleFailure(
        throwable: Throwable,
        onFailure: (Throwable) -> Unit
    ) {
        throwable.message?.let { message ->
            onFailure(GifRequestFailedException(message))
        } ?: onFailure(
                GifRequestFailedException(
                        context.getString(string.gifs_list_search_returned_unknown_error)
                )
        )
    }

    /**
     * Every GIF returned by the Tenor will be available as [Result], to better interface
     * with our app, it will be converted to [MutableGiphyMediaViewModel] to avoid any external
     * coupling with the Tenor API
     */
    private fun Result.toMutableGifMediaViewModel() = MutableGiphyMediaViewModel(
            id,
            Uri.parse(urlFromCollectionFormat(MediaCollectionFormat.GIF_NANO)),
            Uri.parse(urlFromCollectionFormat(MediaCollectionFormat.GIF_TINY)),
            Uri.parse(urlFromCollectionFormat(MediaCollectionFormat.GIF)),
            title
    )

    private fun Result.urlFromCollectionFormat(format: String) =
            medias.firstOrNull()?.get(format)?.url

    /**
     * Since the Tenor only allows a maximum of 50 GIFs per request, the API will throw
     * an exception if this rule is disrespected, in order to still be resilient in
     * provide the desired search, the loadSize will be reduced to the maximum allowed
     * if needed
     */
    private val Int?.fittedToMaximumAllowed
        get() = this?.let {
            when {
                this > MAXIMUM_ALLOWED_LOAD_SIZE -> MAXIMUM_ALLOWED_LOAD_SIZE
                else -> this
            }
        } ?: MAXIMUM_ALLOWED_LOAD_SIZE

    companion object {
        /**
         * To better refers to the Tenor API maximum GIF limit per request
         */
        private const val MAXIMUM_ALLOWED_LOAD_SIZE = 50
    }
}
