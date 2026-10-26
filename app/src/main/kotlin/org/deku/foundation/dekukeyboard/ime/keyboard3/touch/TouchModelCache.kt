/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.deku.foundation.dekukeyboard.ime.keyboard3.touch

import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.CoroutineScope
import org.k3lp.model.K3Model

private const val MAX_CACHED_TOUCH_MODELS = 8

class TouchModelCache {
    // Keyboard models change when language, layout, or modifiers change. Bound this cache so
    // rapid language/layout experimentation cannot retain an unbounded number of touch models.
    // The LRU policy keeps the most recently used layouts hot while allowing older ones to be
    // reclaimed; eight entries matches the compiled K3-model cache in Loader.kt.
    private val byK3Model = Cache.Builder<Pair<K3Model, TouchModelOptions>, TouchModel>()
        .maximumCacheSize(MAX_CACHED_TOUCH_MODELS)
        .build()

    fun getFor(
        model: K3Model,
        options: TouchModelOptions,
    ): TouchModel? {
        return byK3Model.get(model to options)
    }

    context(scope: CoroutineScope)
    suspend fun getOrComputeFor(
        model: K3Model,
        options: TouchModelOptions,
    ): TouchModel {
        return byK3Model.get(model to options) { computeTouchModel(model, options) }
    }
}
