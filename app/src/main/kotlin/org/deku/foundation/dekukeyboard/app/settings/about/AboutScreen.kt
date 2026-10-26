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

package org.deku.foundation.dekukeyboard.app.settings.about

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.deku.foundation.dekukeyboard.BuildConfig
import org.deku.foundation.dekukeyboard.R
import org.deku.foundation.dekukeyboard.app.LocalNavController
import org.deku.foundation.dekukeyboard.app.Routes
import org.deku.foundation.dekukeyboard.clipboardManager
import org.deku.foundation.dekukeyboard.lib.compose.DekuScreen
import org.deku.foundation.dekukeyboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.ui.Preference
import org.deku.foundation.dekukeyboard.lib.android.stringRes
import org.deku.foundation.dekukeyboard.lib.compose.DekuCanvasIcon
import org.deku.foundation.dekukeyboard.lib.compose.stringRes

@Composable
fun AboutScreen() = DekuScreen {
    title = stringRes(R.string.about__title)

    val navController = LocalNavController.current
    val context = LocalContext.current
    val clipboardManager by context.clipboardManager()

    val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    content {
        Column(
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 32.dp)
        ) {
            DekuCanvasIcon(
                modifier = Modifier.requiredSize(64.dp),
                iconId = R.mipmap.deku_app_icon,
                contentDescription = "DekuKeyboard app icon",
            )
            Text(
                text = stringRes(R.string.deku_app_name),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Preference(
            icon = Icons.Outlined.Info,
            title = stringRes(R.string.about__version__title),
            summary = appVersion,
            onClick = {
                try {
                    clipboardManager.addNewPlaintext(appVersion)
                    Toast.makeText(context, R.string.about__version_copied__title, Toast.LENGTH_SHORT).show()
                } catch (e: Throwable) {
                    Toast.makeText(
                        context,
                        context.stringRes(R.string.about__version_copied__error, "error_message" to e.message),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
        Preference(
            icon = Icons.Default.History,
            title = stringRes(R.string.about__changelog__title),
            summary = stringRes(R.string.about__changelog__summary),
            onClick = { context.launchUrl(R.string.dekukeyboard__changelog_url, "version" to BuildConfig.VERSION_NAME) },
        )
        Preference(
            icon = Icons.Outlined.Info,
            title = stringRes(R.string.about__website__title),
            summary = stringRes(R.string.about__website__summary),
            onClick = { context.launchUrl(R.string.dekukeyboard__website_url) },
        )
        Preference(
            icon = Icons.Default.Code,
            title = stringRes(R.string.about__repository__title),
            summary = stringRes(R.string.about__repository__summary),
            onClick = { context.launchUrl(R.string.dekukeyboard__repo_url) },
        )
        Preference(
            icon = Icons.Outlined.Policy,
            title = stringRes(R.string.about__privacy_policy__title),
            summary = stringRes(R.string.about__privacy_policy__summary),
            onClick = { context.launchUrl(R.string.dekukeyboard__privacy_policy_url) },
        )
        Preference(
            icon = Icons.Outlined.Description,
            title = stringRes(R.string.about__project_license__title),
            summary = stringRes(R.string.about__project_license__summary, "license_name" to "Apache 2.0"),
            onClick = { navController.navigate(Routes.Settings.ProjectLicense) },
        )
        Preference(
            icon = Icons.Outlined.Description,
            title = stringRes(id = R.string.about__third_party_licenses__title),
            summary = stringRes(id = R.string.about__third_party_licenses__summary),
            onClick = { navController.navigate(Routes.Settings.ThirdPartyLicenses) },
        )
    }
}
