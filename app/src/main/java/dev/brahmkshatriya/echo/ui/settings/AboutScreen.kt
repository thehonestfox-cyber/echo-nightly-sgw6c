package dev.brahmkshatriya.echo.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import dev.brahmkshatriya.echo.BuildConfig
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.ui.components.ListScreen
import dev.brahmkshatriya.echo.ui.components.SectionHeader
import dev.brahmkshatriya.echo.utils.ContextUtils.getArch

@Composable
fun AboutScreen() {
    ListScreen {
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painterResource(R.drawable.ic_mono), null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colors.primary
                )
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_TYPE} · ${getArch()}",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        item { SectionHeader(stringResource(R.string.about)) }
        item {
            Text(
                stringResource(R.string.about_text),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center
            )
        }
        item { SectionHeader(stringResource(R.string.source_code)) }
        item {
            Text(
                "github.com/${stringResource(R.string.app_github_repo)}\n\n" +
                        stringResource(R.string.based_on_echo),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
