package app.multiauth.ui.oauth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.multiauth.ui.theme.oauth_apple
import app.multiauth.ui.theme.oauth_facebook
import app.multiauth.ui.theme.oauth_github
import app.multiauth.ui.theme.oauth_google
import app.multiauth.ui.theme.oauth_linkedin
import app.multiauth.ui.theme.oauth_twitter

data class OAuthProvider(
    val id: String,
    val name: String,
    val displayName: String,
    val color: Color,
    val icon: String // In a real app, this would be an actual icon resource
)

@Composable
fun OAuthProviderSelection(
    onProviderSelected: (OAuthProvider) -> Unit,
    modifier: Modifier = Modifier
) {
    val providers = listOf(
        OAuthProvider("google", "google", "Google", oauth_google, "G"),
        OAuthProvider("apple", "apple", "Apple", oauth_apple, "A"),
        OAuthProvider("facebook", "facebook", "Facebook", oauth_facebook, "F"),
        OAuthProvider("twitter", "twitter", "Twitter", oauth_twitter, "T"),
        OAuthProvider("github", "github", "GitHub", oauth_github, "GH"),
        OAuthProvider("linkedin", "linkedin", "LinkedIn", oauth_linkedin, "LI")
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Or continue with",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Provider Grid
        providers.chunked(2).forEach { rowProviders ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowProviders.forEach { provider ->
                    OAuthProviderButton(
                        provider = provider,
                        onClick = { onProviderSelected(provider) },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Add empty space if odd number of providers
                if (rowProviders.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OAuthProviderButton(
    provider: OAuthProvider,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Provider Icon (placeholder)
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        color = provider.color,
                        shape = RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = provider.icon,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = provider.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun OAuthProviderButtonLarge(
    provider: OAuthProvider,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Provider Icon (placeholder)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = provider.color,
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = provider.icon,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = "Continue with ${provider.displayName}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}