/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package org.deku.foundation.dekukeyboard.lib

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import kotlinx.coroutines.flow.map

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
inline fun <V : Any, R : Any> PreferenceData<V>.observeAsTransformingState(
    crossinline transform: @DisallowComposableCalls (V) -> R,
): State<R> {
    // Building the mapped flow outside remember created a new flow object on every
    // recomposition, which resubscribed the collector and defeated flow equality.
    val initialValue = remember(this) { transform(asFlow().value) }
    val transformedFlow = remember(this) { asFlow().map { transform(it) } }
    return transformedFlow.collectAsState(initialValue)
}
