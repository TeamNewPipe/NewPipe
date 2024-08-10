package org.schabi.newpipe.ui.components.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.R
import org.schabi.newpipe.util.external_communication.ShareUtils

private val SOFTWARE_COMPONENTS = listOf(
    SoftwareComponent(
        "ACRA", "2013", "Kevin Gaudin",
        "https://github.com/ACRA/acra", StandardLicenses.APACHE2
    ),
    SoftwareComponent(
        "AndroidX", "2005 - 2011", "The Android Open Source Project",
        "https://developer.android.com/jetpack", StandardLicenses.APACHE2
    ),
    SoftwareComponent(
        "Coil", "2023", "Coil Contributors",
        "https://coil-kt.github.io/coil/", StandardLicenses.APACHE2
    ),
    SoftwareComponent(
        "ExoPlayer", "2014 - 2020", "Google, Inc.",
        "https://github.com/google/ExoPlayer", StandardLicenses.APACHE2
    ),
    SoftwareComponent(
        "GigaGet", "2014 - 2015", "Peter Cai",
        "https://github.com/PaperAirplane-Dev-Team/GigaGet", StandardLicenses.GPL3
    ),
    SoftwareComponent(
        "Groupie", "2016", "Lisa Wray",
        "https://github.com/lisawray/groupie", StandardLicenses.MIT
    ),
    SoftwareComponent(
        "Icepick", "2015", "Frankie Sardo",
        "https://github.com/frankiesardo/icepick", StandardLicenses.EPL1
    ),
    SoftwareComponent(
        "Jsoup", "2009 - 2020", "Jonathan Hedley",
        "https://github.com/jhy/jsoup", StandardLicenses.MIT
    ),
    SoftwareComponent(
        "LazyColumnScrollbar", "2024", "nani",
        "https://github.com/nanihadesuka/LazyColumnScrollbar", StandardLicenses.MIT
    ),
    SoftwareComponent(
        "Markwon", "2019", "Dimitry Ivanov",
        "https://github.com/noties/Markwon", StandardLicenses.APACHE2
    ),
    SoftwareComponent(
        "Material Components for Android", "2016 - 2020", "Google, Inc.",
        "https://github.com/material-components/material-components-android",
        StandardLicenses.APACHE2
    ),
    SoftwareComponent(
        "NewPipe Extractor", "2017 - 2020", "Christian Schabesberger",
        "https://github.com/TeamNewPipe/NewPipeExtractor", StandardLicenses.GPL3
    ),
    SoftwareComponent(
        "NoNonsense-FilePicker", "2016", "Jonas Kalderstam",
        "https://github.com/spacecowboy/NoNonsense-FilePicker", StandardLicenses.MPL2
    ),
    SoftwareComponent(
        "OkHttp", "2019", "Square, Inc.",
        "https://square.github.io/okhttp/", StandardLicenses.APACHE2
    ),
    SoftwareComponent(
        "PrettyTime", "2012 - 2020", "Lincoln Baxter, III",
        "https://github.com/ocpsoft/prettytime", StandardLicenses.APACHE2
    ),
    SoftwareComponent(
        "ProcessPhoenix", "2015", "Jake Wharton",
        "https://github.com/JakeWharton/ProcessPhoenix", StandardLicenses.APACHE2
    ),
    SoftwareComponent(
        "RxAndroid", "2015", "The RxAndroid authors",
        "https://github.com/ReactiveX/RxAndroid", StandardLicenses.APACHE2
    ),
    SoftwareComponent(
        "RxBinding", "2015", "Jake Wharton",
        "https://github.com/JakeWharton/RxBinding", StandardLicenses.APACHE2
    ),
    SoftwareComponent(
        "RxJava", "2016 - 2020", "RxJava Contributors",
        "https://github.com/ReactiveX/RxJava", StandardLicenses.APACHE2
    ),
    SoftwareComponent(
        "SearchPreference", "2018", "ByteHamster",
        "https://github.com/ByteHamster/SearchPreference", StandardLicenses.MIT
    )
)

@Composable
@NonRestartableComposable
fun LicenseTab() {
    var selectedLicense by remember { mutableStateOf<SoftwareComponent?>(null) }
    val onClick = remember {
        { it: SoftwareComponent -> selectedLicense = it }
    }

    Text(
        text = stringResource(R.string.app_license_title),
        style = MaterialTheme.typography.titleLarge
    )
    Text(
        text = stringResource(R.string.app_license),
        style = MaterialTheme.typography.bodyMedium
    )

    Text(
        text = stringResource(R.string.title_licenses),
        style = MaterialTheme.typography.titleLarge,
    )
    for (component in SOFTWARE_COMPONENTS) {
        LicenseItem(component, onClick)
    }

    selectedLicense?.let {
        var formattedLicense by remember { mutableStateOf("") }

        val context = LocalContext.current
        LaunchedEffect(key1 = it) {
            formattedLicense = withContext(Dispatchers.IO) {
                it.license.getFormattedLicense(context)
            }
        }

        AlertDialog(
            onDismissRequest = { selectedLicense = null },
            confirmButton = {
                TextButton(onClick = { ShareUtils.openUrlInApp(context, it.link) }) {
                    Text(text = stringResource(R.string.open_website_license))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedLicense = null }) {
                    Text(text = stringResource(R.string.done))
                }
            },
            title = {
                Text(text = it.name, color = MaterialTheme.colorScheme.onBackground)
            },
            text = {
                val styles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
                Text(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    text = AnnotatedString.fromHtml(formattedLicense, styles)
                )
            }
        )
    }
}

@Composable
@NonRestartableComposable
private fun LicenseItem(
    softwareComponent: SoftwareComponent,
    onClick: (SoftwareComponent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(softwareComponent) }
    ) {
        Text(text = softwareComponent.name)
        Text(
            style = MaterialTheme.typography.bodyMedium,
            text = stringResource(
                R.string.copyright, softwareComponent.years,
                softwareComponent.copyrightOwner, softwareComponent.license.abbreviation
            )
        )
    }
}
