package com.example.volunteersApp.date

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.ui.main.MainActivity
import com.example.volunteersApp.ui.main.MainViewModel
import com.example.volunteersApp.ui.main.SocialInboxNav
import com.example.volunteersApp.ui.shared.AiResponseDialog
import com.example.volunteersApp.ui.shared.SearchableGlobalCountryDropdown
import com.example.volunteersApp.wallet.globalCountries
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BlindDateJoinStep {
    DETAILS,
    PAYMENT
}

private data class BlindDateJoinDraft(
    val bio: String,
    val mediaUris: List<Uri>,
    val gender: Gender,
    val lookingFor: LookingFor,
)

/** MindLoom-inspired light surfaces for Dating Hub and Loop. */
private val DateHubSurfaceGradient = listOf(
    Color(0xFFF6F8FC),
    Color(0xFFEAF1FB),
    Color(0xFFF8FAFF)
)
private val DateHubHeroGradientStart = Color(0xFFFFF2CC)
private val DateHubHeroGradientMid = Color(0xFFE7F2FF)
private val DateHubHeroGradientEnd = Color(0xFFDBEBFF)
private val DateHubHighlightGradient = listOf(
    Color(0xFFEAF2FF),
    Color(0xFFFDF3D8)
)
private val DateHubInk = Color(0xFF16263F)
private val DateHubMutedInk = Color(0xFF314A6B)

/** Colorblind-friendly accents (blue + orange separation). */
private val DateHubDatingLoopAccent = Color(0xFF0072B2)
private val DateHubBlindDateAccent = Color(0xFFE69F00)

/**
 * Force a light, high-contrast palette in Dating Hub so the UI avoids black backgrounds
 * and remains readable across color-vision deficiencies.
 */
private val DateHubAccessibleColorScheme = lightColorScheme(
    primary = Color(0xFF005EA2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E9FF),
    onPrimaryContainer = Color(0xFF001C37),
    secondary = Color(0xFF8B5A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4BF),
    onSecondaryContainer = Color(0xFF2F1900),
    tertiary = Color(0xFF006A67),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA7F2ED),
    onTertiaryContainer = Color(0xFF00201F),
    error = Color(0xFFB42318),
    onError = Color.White,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF5A130F),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE6ECF5),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFF60758D),
    outlineVariant = Color(0xFFB8C4D6)
)

private fun blindDateStatusLabel(status: BlindDateUserStatus): String =
    when (status) {
        BlindDateUserStatus.NotJoined -> "Not Joined"
        BlindDateUserStatus.AwaitingPayment -> "Awaiting payment"
        BlindDateUserStatus.Active -> "Active"
        BlindDateUserStatus.Matched -> "Matched"
        BlindDateUserStatus.Expired -> "Expired"
    }

@Composable
private fun ConsumeBlindDateDeepLinkEffect(
    mainViewModel: MainViewModel?,
    uiRoute: DateHubRoute,
    onConsumeAndRequestBlind: () -> Unit,
) {
    if (mainViewModel == null) return
    val pending by mainViewModel.pendingBlindDateDeepLink.collectAsState()
    LaunchedEffect(pending, uiRoute) {
        if (!pending || uiRoute != DateHubRoute.DASHBOARD) return@LaunchedEffect
        mainViewModel.clearPendingBlindDateDeepLink()
        onConsumeAndRequestBlind()
    }
}

@Composable
fun DateEvaScreen(
    viewModel: DateEvaViewModel,
    blindDateViewModel: BlindDateViewModel,
    vertexViewModel: VertexViewModel,
    mainViewModel: MainViewModel? = null,
    onOpenInbox: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var showAiResponseDialog by remember { mutableStateOf(false) }
    var showAgeDialog by remember { mutableStateOf(false) }
    var showUnderAgeDialog by remember { mutableStateOf(false) }
    var showDatingRestrictedDialog by remember { mutableStateOf(false) }
    var showBlindDateDisabledDialog by remember { mutableStateOf(false) }
    var showRequireDatingProfileDialog by remember { mutableStateOf(false) }

    val launchAiPrompt: (String) -> Unit = remember(vertexViewModel) {
        { prompt ->
            FirebaseAppCheck.getInstance().getToken(false)
                .addOnSuccessListener { vertexViewModel.generate(prompt) }
                .addOnFailureListener { Log.e("DateHub", "App Check token unavailable.", it) }
        }
    }

    fun handleAccessResult(result: DateAccessResult) {
        when (result) {
            DateAccessResult.RequireAge -> showAgeDialog = true
            DateAccessResult.UnderAge -> showUnderAgeDialog = true
            DateAccessResult.DatingRestricted -> showDatingRestrictedDialog = true
            DateAccessResult.BlindDateDisabled -> showBlindDateDisabledDialog = true
            DateAccessResult.RequireDatingLoopProfile -> showRequireDatingProfileDialog = true
            DateAccessResult.Opened,
            DateAccessResult.RedirectedToProfile -> Unit
        }
    }

    ConsumeBlindDateDeepLinkEffect(
        mainViewModel = mainViewModel,
        uiRoute = uiState.route,
        onConsumeAndRequestBlind = { handleAccessResult(viewModel.requestBlindDate()) }
    )

    LaunchedEffect(uiState.accountCanAccessDating) {
        if (uiState.accountCanAccessDating == true) {
            blindDateViewModel.refreshOverview()
        }
    }

    LaunchedEffect(aiResponse) {
        if (aiResponse != null) {
            showAiResponseDialog = true
        }
    }

    MaterialTheme(
        colorScheme = DateHubAccessibleColorScheme,
        typography = MaterialTheme.typography
    ) {
        if (showAiResponseDialog) {
            AiResponseDialog(
                generatedText = aiResponse.orEmpty(),
                onDismiss = {
                    showAiResponseDialog = false
                    vertexViewModel.clearResponse()
                }
            )
        }

        if (showAgeDialog) {
            AgeVerificationDialog(
                onDismiss = { showAgeDialog = false },
                onConfirm = { age ->
                    showAgeDialog = false
                    handleAccessResult(viewModel.confirmAge(age))
                }
            )
        }

        if (showUnderAgeDialog) {
            UnderAgeDialog(onDismiss = { showUnderAgeDialog = false })
        }

        if (showDatingRestrictedDialog) {
            AlertDialog(
                onDismissRequest = { showDatingRestrictedDialog = false },
                title = { Text("Dating not available") },
                text = {
                    Text("Discover and Blind Date are turned off for this account. If you think this is a mistake, contact support.")
                },
                confirmButton = {
                    TextButton(onClick = { showDatingRestrictedDialog = false }) { Text("OK") }
                }
            )
        }

        if (showBlindDateDisabledDialog) {
            AlertDialog(
                onDismissRequest = { showBlindDateDisabledDialog = false },
                title = { Text("Blind Date unavailable") },
                text = {
                    Text("Blind Date is currently turned off. Check back later or contact support.")
                },
                confirmButton = {
                    TextButton(onClick = { showBlindDateDisabledDialog = false }) { Text("OK") }
                }
            )
        }

        if (showRequireDatingProfileDialog) {
            AlertDialog(
                onDismissRequest = { showRequireDatingProfileDialog = false },
                title = { Text("Dating profile required") },
                text = {
                    Text(
                        "Blind Date requires a completed Dating Loop profile (name, ${DATING_BIO_MIN_LENGTH}+ character bio, country, and at least two photos). " +
                            "Create or finish your profile first, then come back to Blind Date."
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showRequireDatingProfileDialog = false }) { Text("Cancel") }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRequireDatingProfileDialog = false
                            handleAccessResult(viewModel.requestDatingLoop())
                        }
                    ) {
                        Text("Open Discover")
                    }
                }
            )
        }

        when (uiState.route) {
            DateHubRoute.DASHBOARD -> DateHubDashboardScreen(
                uiState = uiState,
                onDismissBanner = viewModel::dismissBanner,
                onRefreshDashboard = {
                    viewModel.refreshDashboard(
                        onWalletRefresh = { viewModel.walletViewModel.refresh() },
                        onBlindRefresh = { blindDateViewModel.refreshOverview() },
                    )
                },
                onOpenDatingLoop = { handleAccessResult(viewModel.requestDatingLoop()) },
                onOpenBlindDate = { handleAccessResult(viewModel.requestBlindDate()) },
                onOpenProfile = {
                    handleAccessResult(
                        viewModel.requestDatingProfile(returnTo = DateHubRoute.DASHBOARD)
                    )
                },
                onRequestAgeVerification = {
                    handleAccessResult(viewModel.requestAgeVerificationFromDashboard())
                },
                onShare = { shareDateHubInvite(context, ShareInviteTarget.DatingHub) }
            )

            DateHubRoute.DATING_LOOP -> DatingLoopScreen(
                uiState = uiState,
                onBack = viewModel::navigateBack,
                onDismissBanner = viewModel::dismissBanner,
                onOpenProfile = {
                    handleAccessResult(
                        viewModel.requestDatingProfile(returnTo = DateHubRoute.DATING_LOOP)
                    )
                },
                onTryBlindDate = { handleAccessResult(viewModel.requestBlindDate()) },
                onAskAi = {
                    launchAiPrompt(
                        "You are the Dating Loop assistant. Suggest one respectful, warm first message someone could send after finding a profile in the community dating loop."
                    )
                },
                onSearchQueryChange = viewModel::setDatingLoopSearchQuery,
                onGenderFilterChange = viewModel::setDatingLoopGenderFilter,
                onCountryFilterChange = viewModel::setDatingLoopCountryFilter,
                onHasPhotosOnlyChange = viewModel::setDatingLoopHasPhotosOnly,
                onBrowseLayoutChange = viewModel::setDatingBrowseLayout,
                onClearFilters = {
                    viewModel.setDatingLoopSearchQuery("")
                    viewModel.setDatingLoopGenderFilter(null)
                    viewModel.setDatingLoopCountryFilter(null)
                    viewModel.setDatingLoopHasPhotosOnly(false)
                },
                onInvite = { profile, onMessage ->
                    viewModel.sendChatInvitation(profile) { success, message ->
                        onMessage(
                            DateHubBannerMessage(
                                message,
                                if (success) DateHubBannerTone.Success else DateHubBannerTone.Error
                            )
                        )
                    }
                },
                onBlockUser = { profile, onMessage ->
                    viewModel.blockDatingUser(profile.uid) { success, message ->
                        onMessage(
                            DateHubBannerMessage(
                                message,
                                if (success) DateHubBannerTone.Success else DateHubBannerTone.Error
                            )
                        )
                    }
                }
            )

            DateHubRoute.DATING_PROFILE -> DatingProfileEditorScreen(
                uiState = uiState,
                onBack = viewModel::cancelProfileEditing,
                onDismissBanner = viewModel::dismissBanner,
                onSave = { name, bio, phone, country, gender, lookingFor, imageUris ->
                    viewModel.createProfile(name, bio, phone, country, gender, lookingFor, imageUris)
                },
                onDelete = viewModel::deleteProfile,
                onShare = { shareDateHubInvite(context, ShareInviteTarget.DatingProfile) },
                onAskAi = { name, bio ->
                    launchAiPrompt(
                        "Help refine this dating profile. Name: ${name.ifBlank { "Unknown" }}. Draft bio: ${bio.ifBlank { "No bio yet." }}. Rewrite it into a friendly 2-3 sentence bio for a volunteer-focused dating community."
                    )
                }
            )

            DateHubRoute.BLIND_DATE -> BlindDateScreen(
                datingState = uiState,
                blindDateViewModel = blindDateViewModel,
                onBack = viewModel::navigateBack,
                onOpenProfile = {
                    handleAccessResult(
                        viewModel.requestDatingProfile(returnTo = DateHubRoute.BLIND_DATE)
                    )
                },
                onAskAi = {
                    launchAiPrompt(
                        "You are the Blind Date assistant. Blind Date is separate from Dating Loop: users can join with only a blind-date profile. Give one etiquette tip for respectful invites and one warm line for a blind-date bio."
                    )
                },
                onOpenInbox = onOpenInbox
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateHubDashboardScreen(
    uiState: DateEvaUiState,
    onDismissBanner: () -> Unit,
    onRefreshDashboard: () -> Unit,
    onOpenDatingLoop: () -> Unit,
    onOpenBlindDate: () -> Unit,
    onOpenProfile: () -> Unit,
    onRequestAgeVerification: () -> Unit,
    onShare: () -> Unit
) {
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = uiState.isRefreshing)
    val screenBrush = Brush.verticalGradient(
        colors = DateHubSurfaceGradient
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBrush)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Dating Hub",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = DateHubInk
                        )
                    },
                    actions = {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = "Share Dating Hub")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.statusBars
        ) { padding ->
            SwipeRefresh(
                state = swipeRefreshState,
                onRefresh = onRefreshDashboard,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        uiState.bannerMessage?.let {
                            DateHubStatusBanner(
                                message = it,
                                onDismiss = onDismissBanner
                            )
                        }
                    }
                    if (uiState.accountCanAccessDating == false) {
                        item {
                            DateHubRestrictedInfoStrip()
                        }
                    }
                    item {
                        DateHubHeroCard(
                            accountCanAccessDating = uiState.accountCanAccessDating,
                            hasVerifiedAge = uiState.hasVerifiedAge,
                            hasDatingProfile = uiState.myProfile != null,
                            profileCompletenessPercent = uiState.profileCompletenessPercent,
                            compatibleCount = uiState.compatibleProfilesCount,
                            blindStatus = uiState.myBlindDateStatus,
                            pendingInvitesCount = uiState.pendingBlindInvitations,
                            outgoingInvitesCount = uiState.outgoingBlindInvitations,
                            timelineEventCount = uiState.blindTimelineEventCount,
                            isStaffExempt = uiState.isStaffExempt
                        )
                    }
                    if (!uiState.hasVerifiedAge && uiState.accountCanAccessDating != false) {
                        item {
                            DateHubAgeGateCard(onConfirmAge = onRequestAgeVerification)
                        }
                    }
                    item {
                        DateHubChooseLoopCard(
                            hasDatingProfile = uiState.myProfile != null,
                            blindStatus = uiState.myBlindDateStatus,
                            enableBlindDate = uiState.enableBlindDate,
                            onOpenDatingLoop = onOpenDatingLoop,
                            onOpenBlindDate = onOpenBlindDate,
                            onOpenProfile = onOpenProfile
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHubRestrictedInfoStrip() {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "Dating access restricted. Discover and Blind Date are unavailable for this account."
            },
        shape = shape,
        color = Color(0xFF3949AB).copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color(0xFF3949AB).copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFF3949AB),
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "Dating data is restricted for this account. Experiences stay read-only until access is restored.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DateHubHeroCard(
    accountCanAccessDating: Boolean?,
    hasVerifiedAge: Boolean,
    hasDatingProfile: Boolean,
    profileCompletenessPercent: Int,
    compatibleCount: Int,
    blindStatus: BlindDateUserStatus,
    pendingInvitesCount: Int,
    outgoingInvitesCount: Int,
    timelineEventCount: Int,
    isStaffExempt: Boolean
) {
    val accessStatusLabel = when {
        accountCanAccessDating == false -> "Dating access is restricted"
        hasVerifiedAge -> "18+ access is ready"
        else -> "18+ check before Discover or Blind Date"
    }
    val summaryLine = when {
        accountCanAccessDating == false ->
            "Discover and Blind Date are unavailable for this account."
        else ->
            "Discover people, share intent, and connect respectfully."
    }
    val heroShape = RoundedCornerShape(24.dp)
    val heroBrush = Brush.linearGradient(
        colors = listOf(DateHubHeroGradientStart, DateHubHeroGradientMid, DateHubHeroGradientEnd),
        start = Offset.Zero,
        end = Offset(400f, 520f)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Dating Hub. Adults only community matching. $accessStatusLabel. $summaryLine"
            }
            .shadow(elevation = 6.dp, shape = heroShape)
            .clip(heroShape)
            .background(heroBrush)
            .border(1.dp, Color(0xFFC7D9F4), heroShape)
            .padding(20.dp)
    ) {
        if (isStaffExempt) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd),
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, Color(0xFFC7D9F4))
            ) {
                Text(
                    text = "Staff exempt",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = DateHubInk,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "ADULTS ONLY - COMMUNITY MATCHING",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = DateHubMutedInk
            )
            Text(
                text = "Dating Hub",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = DateHubInk,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = summaryLine,
                style = MaterialTheme.typography.bodyMedium,
                color = DateHubMutedInk
            )
            Text(
                text = accessStatusLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = DateHubInk
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                DateHubMetaChip(
                    label = if (hasVerifiedAge) "18+ confirmed" else "18+ required",
                    containerColor = Color.White.copy(alpha = 0.72f)
                )
                DateHubMetaChip(
                    label = if (hasDatingProfile) "Profile $profileCompletenessPercent%" else "Profile needed",
                    containerColor = Color.White.copy(alpha = 0.72f)
                )
            }
            if (hasDatingProfile) {
                LinearProgressIndicator(
                    progress = { profileCompletenessPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    color = DateHubInk,
                    trackColor = Color.White.copy(alpha = 0.45f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateHubHeroStatTile(
                    label = "Compatible",
                    value = compatibleCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                DateHubHeroStatTile(
                    label = "Blind status",
                    value = blindDateStatusLabel(blindStatus),
                    modifier = Modifier.weight(1f)
                )
                DateHubHeroStatTile(
                    label = "Pending invites",
                    value = pendingInvitesCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateHubHeroStatTile(
                    label = "Sent invites",
                    value = outgoingInvitesCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                DateHubHeroStatTile(
                    label = "Timeline events",
                    value = timelineEventCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DateHubMetaChip(label: String, containerColor: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(1.dp, Color(0xFFC7D9F4))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = DateHubInk,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun DateHubHeroStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, Color(0xFFC7D9F4))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = DateHubMutedInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = DateHubInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateHubAgeGateCard(onConfirmAge: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Age verification",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Confirm you are 18 or older before opening Discover or Blind Date. This one step unlocks both experiences on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(
                onClick = onConfirmAge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Confirm age")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateHubExperienceRouteRow(
    title: String,
    subtitle: String,
    noteLabel: String,
    accentLabel: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    accessibilityDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tileShape = RoundedCornerShape(16.dp)
    val iconTileShape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDescription
            },
        shape = tileShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(iconTileShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(accent, accent.copy(alpha = 0.72f)),
                            start = Offset.Zero,
                            end = Offset(48f, 48f)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = noteLabel,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accent.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = accentLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateHubChooseLoopCard(
    hasDatingProfile: Boolean,
    blindStatus: BlindDateUserStatus,
    enableBlindDate: Boolean,
    onOpenDatingLoop: () -> Unit,
    onOpenBlindDate: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Choose your experience. Discover and Blind Date." },
        shape = panelShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Choose your experience",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            DateHubExperienceRouteRow(
                title = "Dating Loop",
                subtitle = "Browse compatible profiles and send chat invitations.",
                noteLabel = "Age check required",
                accentLabel = if (hasDatingProfile) "Profile live" else "Create profile",
                icon = Icons.Default.Favorite,
                accent = DateA11yPalette.accentDiscover,
                onClick = onOpenDatingLoop,
                accessibilityDescription = "Dating Loop. Age check required. Tap to open."
            )
            DateHubExperienceRouteRow(
                title = "Blind Date",
                subtitle = if (enableBlindDate) {
                    "Curated invites, matches, and private chat."
                } else {
                    "Temporarily disabled by admin."
                },
                noteLabel = "Age + profile required",
                accentLabel = if (enableBlindDate) blindDateStatusLabel(blindStatus) else "Disabled",
                icon = Icons.Default.Groups,
                accent = DateHubBlindDateAccent,
                onClick = onOpenBlindDate,
                enabled = enableBlindDate,
                accessibilityDescription = "Blind Date. Age and dating profile required. Tap to open."
            )
            DateHubDatingProfileShortcut(onClick = onOpenProfile)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateHubDatingProfileShortcut(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Dating profile. Edit basics, preferences, and photos."
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Dating profile",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Edit basics, preferences, and photos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DateHubLiveMetricsCard(
    datingCollections: Int,
    blindCollections: Int,
    pendingInvites: Int,
    timelineEvents: Int
) {
    val panelShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = panelShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Live metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DateHubMetricCell(
                        value = datingCollections.toString(),
                        label = "Dating collections",
                        modifier = Modifier.weight(1f)
                    )
                    DateHubMetricCell(
                        value = blindCollections.toString(),
                        label = "Blind collections",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DateHubMetricCell(
                        value = pendingInvites.toString(),
                        label = "Pending invites",
                        modifier = Modifier.weight(1f)
                    )
                    DateHubMetricCell(
                        value = timelineEvents.toString(),
                        label = "Timeline events",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DateHubMetricCell(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatingLoopScreen(
    uiState: DateEvaUiState,
    onBack: () -> Unit,
    onDismissBanner: () -> Unit,
    onOpenProfile: () -> Unit,
    onTryBlindDate: () -> Unit,
    onAskAi: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onGenderFilterChange: (Gender?) -> Unit,
    onCountryFilterChange: (String?) -> Unit,
    onHasPhotosOnlyChange: (Boolean) -> Unit,
    onBrowseLayoutChange: (DatingBrowseLayout) -> Unit,
    onClearFilters: () -> Unit,
    onInvite: (DatingProfile, (DateHubBannerMessage) -> Unit) -> Unit,
    onBlockUser: (DatingProfile, (DateHubBannerMessage) -> Unit) -> Unit,
) {
    val discoverableProfiles = uiState.filteredLoopProfiles
    var localBanner by remember { mutableStateOf<DateHubBannerMessage?>(null) }
    var selectedProfile by remember { mutableStateOf<DatingProfile?>(null) }
    var blockTarget by remember { mutableStateOf<DatingProfile?>(null) }
    val filters = uiState.datingLoopFilters
    val hasActiveFilters = filters != DatingLoopFilters()
    val availableCountries = remember(uiState.allProfiles) {
        uiState.allProfiles.map { it.country.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val screenBrush = Brush.verticalGradient(colors = DateA11yPalette.pageGradient)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Dating Loop",
                            color = DateA11yPalette.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onAskAi) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Dating assistant")
                        }
                        IconButton(onClick = onOpenProfile) {
                            Icon(Icons.Default.Person, contentDescription = "Open dating profile")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    val banner = localBanner ?: uiState.bannerMessage
                    banner?.let {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        ) {
                            DateHubStatusBanner(
                                message = it,
                                onDismiss = {
                                    localBanner = null
                                    onDismissBanner()
                                }
                            )
                        }
                    }
                }
                item {
                    DateHubBlindDatePromoCard(onClick = onTryBlindDate)
                }
                item {
                    DatingLoopCollectionDigestCard(
                        profileCount = discoverableProfiles.size,
                        photoTotal = discoverableProfiles.sumOf { it.resolvedImageUrls().size },
                    )
                }
                item {
                    DatingLoopFiltersCard(
                        filters = filters,
                        browseLayout = uiState.browseLayout,
                        availableCountries = availableCountries,
                        visibleProfileCount = discoverableProfiles.size,
                        hasActiveFilters = hasActiveFilters,
                        onSearchQueryChange = onSearchQueryChange,
                        onGenderFilterChange = onGenderFilterChange,
                        onCountryFilterChange = onCountryFilterChange,
                        onHasPhotosOnlyChange = onHasPhotosOnlyChange,
                        onBrowseLayoutChange = onBrowseLayoutChange,
                        onClearFilters = onClearFilters,
                    )
                }
                if (uiState.myProfile == null) {
                    item {
                        DateLoopDiscoveryIntroCard(
                            hasProfile = false,
                            onCreateProfile = onOpenProfile
                        )
                    }
                }
                if (discoverableProfiles.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            tonalElevation = 1.dp,
                            shadowElevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "No profiles yet",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "No compatible profiles match your filters yet. Try adjusting search or filters, or check back later.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = DateA11yPalette.textSecondary
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = uiState.browseLayout.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DateA11yPalette.textPrimary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }
                    items(discoverableProfiles, key = { it.uid }) { profile ->
                        val isInviteInFlight = profile.uid in uiState.invitingUserIds
                        if (uiState.browseLayout == DatingBrowseLayout.People) {
                        DatingDiscoveryCard(
                            profile = profile,
                            inviterHasProfile = uiState.myProfile != null,
                                isInviteInFlight = isInviteInFlight,
                            onOpen = { selectedProfile = profile },
                            onInvite = {
                                onInvite(profile) { banner ->
                                    localBanner = banner
                                }
                            }
                        )
                        } else {
                            DatingDiscoveryQuickViewCard(
                                profile = profile,
                                inviterHasProfile = uiState.myProfile != null,
                                isInviteInFlight = isInviteInFlight,
                                onOpen = { selectedProfile = profile },
                                onInvite = {
                                    onInvite(profile) { banner ->
                                        localBanner = banner
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedProfile?.let { profile ->
        DatingProfileDetailDialog(
            profile = profile,
            canSendInvite = uiState.myProfile != null,
            isInviteInFlight = profile.uid in uiState.invitingUserIds,
            onDismiss = { selectedProfile = null },
            onInvite = {
                selectedProfile = null
                onInvite(profile) { banner ->
                    localBanner = banner
                }
            },
            onBlock = {
                selectedProfile = null
                blockTarget = profile
            }
        )
    }

    blockTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { blockTarget = null },
            title = { Text("Block ${profile.name.ifBlank { "this member" }}?") },
            text = {
                Text("They will be removed from Dating Loop and you will not be able to invite them. This does not share your phone number.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        blockTarget = null
                        onBlockUser(profile) { banner -> localBanner = banner }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Block")
                }
            },
            dismissButton = {
                TextButton(onClick = { blockTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DatingLoopFiltersCard(
    filters: DatingLoopFilters,
    browseLayout: DatingBrowseLayout,
    availableCountries: List<String>,
    visibleProfileCount: Int,
    hasActiveFilters: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onGenderFilterChange: (Gender?) -> Unit,
    onCountryFilterChange: (String?) -> Unit,
    onHasPhotosOnlyChange: (Boolean) -> Unit,
    onBrowseLayoutChange: (DatingBrowseLayout) -> Unit,
    onClearFilters: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Find your people", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "$visibleProfileCount compatible profiles",
                        style = MaterialTheme.typography.labelMedium,
                        color = DateA11yPalette.textSecondary,
                    )
                }
                if (hasActiveFilters) {
                    TextButton(onClick = onClearFilters) { Text("Clear filters") }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DatingBrowseLayout.entries.forEach { layout ->
                    FilterChip(
                        selected = browseLayout == layout,
                        onClick = { onBrowseLayoutChange(layout) },
                        label = { Text(layout.label) }
                    )
                }
            }
            OutlinedTextField(
                value = filters.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search profiles") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = filters.genderFilter == null,
                        onClick = { onGenderFilterChange(null) },
                        label = { Text("All genders") }
                    )
                }
                items(Gender.entries) { gender ->
                    FilterChip(
                        selected = filters.genderFilter == gender,
                        onClick = { onGenderFilterChange(gender) },
                        label = { Text(gender.name.lowercase().replaceFirstChar { it.titlecase() }) }
                    )
                }
            }
            if (availableCountries.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filters.countryFilter.isNullOrBlank(),
                            onClick = { onCountryFilterChange(null) },
                            label = { Text("All countries") }
                        )
                    }
                    items(availableCountries) { country ->
                        FilterChip(
                            selected = filters.countryFilter == country,
                            onClick = { onCountryFilterChange(country) },
                            label = { Text(country) }
                        )
                    }
                }
            }
            FilterChip(
                selected = filters.hasPhotosOnly,
                onClick = { onHasPhotosOnlyChange(!filters.hasPhotosOnly) },
                label = { Text("Has photos") }
            )
        }
    }
}

@Composable
private fun DatingLoopCollectionDigestCard(profileCount: Int, photoTotal: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, DateHubDatingLoopAccent.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "COLLECTION DIGEST",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "What's live now",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Counts include every dating profile visible in this feed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$profileCount",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = DateHubDatingLoopAccent
                )
                Text("Profiles", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "$photoTotal",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("Photos", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DateLoopDiscoveryIntroCard(
    hasProfile: Boolean,
    onCreateProfile: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Discovery",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Discovery mode",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (hasProfile) {
                    "Your profile is live. Browse the community and send invitation-style intros from each card."
                } else {
                    "You can browse profiles right away. Add your own profile so others can invite you back."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasProfile) {
                OutlinedButton(onClick = onCreateProfile) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create dating profile")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateHubBlindDatePromoCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Try Blind Date. Curated matches and invite flow."
            },
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, DateHubBlindDateAccent.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(colors = DateHubHighlightGradient)
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = DateHubBlindDateAccent.copy(alpha = 0.16f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = DateHubBlindDateAccent
                    )
                }
                Column {
                    Text(
                        text = "Try Blind Date",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = DateHubInk
                    )
                    Text(
                        text = "Curated matches and invite flow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DateHubMutedInk
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = DateHubBlindDateAccent
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DatingDiscoveryCard(
    profile: DatingProfile,
    inviterHasProfile: Boolean,
    isInviteInFlight: Boolean,
    onOpen: () -> Unit,
    onInvite: () -> Unit
) {
    val photos = remember(profile.uid, profile.imageUrls) { profile.resolvedImageUrls() }
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = DateA11yPalette.cardSurface,
        border = BorderStroke(1.5.dp, DateA11yPalette.cardBorder),
        shadowElevation = 2.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val pagerState = rememberPagerState(pageCount = { photos.size.coerceAtLeast(1) })
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClickLabel = "Open ${profile.name.ifBlank { "member" }} profile",
                        onClick = onOpen
                    )
            ) {
                if (photos.isEmpty()) {
                    AsyncImageDisplayUrl(
                        rawUrl = "",
                        contentDescription = "${profile.name} photo",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        AsyncImageDisplayUrl(
                            rawUrl = photos[page],
                            contentDescription = "${profile.name} photo ${page + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (photos.size > 1) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = DateA11yPalette.mediaBadge
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${photos.size}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(photos.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (pagerState.currentPage == index) {
                                                Color.White
                                            } else {
                                                Color.White.copy(alpha = 0.45f)
                                            }
                                        )
                                )
                            }
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name.ifBlank { "Community member" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DateA11yPalette.textPrimary
                        )
                        Text(
                            text = "${profile.gender.ifBlank { "Other" }} · Looking for ${profile.lookingFor.ifBlank { "Everyone" }}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = DateA11yPalette.accentDiscover
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DateA11yPalette.chipBackground,
                        border = BorderStroke(1.dp, DateA11yPalette.chipBorder.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = profile.country.ifBlank { "Global" },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = DateA11yPalette.chipText
                        )
                    }
                }
                Text(
                    text = profile.bio.ifBlank { "No bio yet." },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    color = DateA11yPalette.textSecondary
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, DateA11yPalette.cardBorderStrong)
                ) {
                    Text("View", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onInvite,
                    modifier = Modifier.weight(1f),
                    enabled = inviterHasProfile && !isInviteInFlight,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DateA11yPalette.accentDiscover,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.MailOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isInviteInFlight) "Sending..." else "Invite", fontWeight = FontWeight.SemiBold)
                }
            }
            if (!inviterHasProfile) {
                Text(
                    "Add your dating profile to send invitations.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = DateA11yPalette.textSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 0.dp)
                )
            }
        }
    }
}

@Composable
private fun DatingDiscoveryQuickViewCard(
    profile: DatingProfile,
    inviterHasProfile: Boolean,
    isInviteInFlight: Boolean,
    onOpen: () -> Unit,
    onInvite: () -> Unit
) {
    val firstPhoto = remember(profile.uid, profile.imageUrls) {
        profile.resolvedImageUrls().firstOrNull().orEmpty()
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = DateA11yPalette.cardSurface,
        border = BorderStroke(1.dp, DateA11yPalette.cardBorder),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImageDisplayUrl(
                rawUrl = firstPhoto,
                contentDescription = "${profile.name.ifBlank { "Member" }} photo",
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = profile.name.ifBlank { "Community member" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DateA11yPalette.textPrimary
                )
                Text(
                    text = profile.country.ifBlank { "Global" },
                    style = MaterialTheme.typography.labelMedium,
                    color = DateA11yPalette.accentDiscover
                )
                Text(
                    text = profile.bio.ifBlank { "No bio yet." },
                    style = MaterialTheme.typography.bodySmall,
                    color = DateA11yPalette.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpen) { Text("View") }
                    TextButton(
                        onClick = onInvite,
                        enabled = inviterHasProfile && !isInviteInFlight
                    ) {
                        Text(if (isInviteInFlight) "Sending..." else "Invite")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DatingProfileDetailDialog(
    profile: DatingProfile,
    canSendInvite: Boolean,
    isInviteInFlight: Boolean,
    onDismiss: () -> Unit,
    onInvite: () -> Unit,
    onBlock: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onInvite, enabled = canSendInvite && !isInviteInFlight) {
                Text(if (isInviteInFlight) "Sending invitation..." else "Send chat invitation")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onBlock) { Text("Block") }
            TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        title = { Text(profile.name.ifBlank { "Dating Profile" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "An invitation starts a private chat request. Your phone number stays private.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DateA11yPalette.textSecondary,
                )
                val dialogPhotos = remember(profile.uid, profile.imageUrls) { profile.resolvedImageUrls() }
                if (dialogPhotos.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { dialogPhotos.size })
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalPager(state = pagerState, modifier = Modifier.height(220.dp)) { page ->
                            AsyncImageDisplayUrl(
                                rawUrl = dialogPhotos[page],
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        if (dialogPhotos.size > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                repeat(dialogPhotos.size) { index ->
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 3.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (pagerState.currentPage == index) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                                }
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
                Text(profile.bio.ifBlank { "No bio yet." })
                Text(
                    "${profile.gender.ifBlank { "Other" }} - Looking for ${profile.lookingFor.ifBlank { "Everyone" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (profile.country.isNotBlank()) {
                    Text("Country: ${profile.country}", style = MaterialTheme.typography.bodySmall)
                }
                if (!canSendInvite) {
                    Text(
                        "Create your dating profile from Discover to send chat invitations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatingProfileEditorScreen(
    uiState: DateEvaUiState,
    onBack: () -> Unit,
    onDismissBanner: () -> Unit,
    onSave: (String, String, String, String, Gender, LookingFor, List<Uri>) -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onAskAi: (String, String) -> Unit
) {
    val existingProfile = uiState.myProfile
    var name by remember(existingProfile) { mutableStateOf(existingProfile?.name.orEmpty()) }
    var bio by remember(existingProfile) { mutableStateOf(existingProfile?.bio.orEmpty()) }
    var phone by remember(existingProfile, uiState.ownerPrivatePhone) {
        mutableStateOf(uiState.ownerPrivatePhone.ifBlank { existingProfile?.phone.orEmpty() })
    }
    var country by remember(existingProfile) {
        mutableStateOf(existingProfile?.country?.takeIf { it.isNotBlank() } ?: Locale.getDefault().displayCountry)
    }
    val availableCountries = remember { globalCountries() }
    var gender by remember(existingProfile, uiState.userGender) {
        mutableStateOf(existingProfile?.gender?.let(Gender::fromRaw) ?: uiState.userGender ?: Gender.OTHER)
    }
    var lookingFor by remember(existingProfile, uiState.lookingFor) {
        mutableStateOf(existingProfile?.lookingFor?.let(LookingFor::fromRaw) ?: uiState.lookingFor ?: LookingFor.EVERYONE)
    }
    var newImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = DATING_LOOP_MAX_PHOTOS),
        onResult = { uris -> newImageUris = uris }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Dating Profile") },
            text = { Text("This removes your Discover profile and also blocks Blind Date until you create one again.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    val editorScreenBrush = Brush.verticalGradient(colors = DateHubSurfaceGradient)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(editorScreenBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (existingProfile == null) "Dating Profile" else "Edit Dating Profile",
                            color = DateHubInk
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { onAskAi(name, bio) }) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Profile assistant")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                uiState.bannerMessage?.let {
                    DateHubStatusBanner(message = it, onDismiss = onDismissBanner)
                }

            EditorSectionCard(title = "Basics", subtitle = "The first things people notice.") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    placeholder = { Text("Tell people what kind of energy you bring.") },
                    supportingText = {
                        Text("At least $DATING_BIO_MIN_LENGTH characters for a complete profile.")
                    }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone (private)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                SearchableGlobalCountryDropdown(
                    selectedCountry = country,
                    countries = availableCountries,
                    onCountrySelected = { country = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            EditorSectionCard(title = "Preferences", subtitle = "Blind Date also depends on these.") {
                Text("Gender", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                PreferenceChipRow(
                    options = Gender.entries,
                    selected = gender,
                    label = { it.name.lowercase().replaceFirstChar(Char::titlecase) },
                    onSelected = { gender = it }
                )
                Spacer(Modifier.height(12.dp))
                Text("Looking for", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                PreferenceChipRow(
                    options = LookingFor.entries,
                    selected = lookingFor,
                    label = {
                        when (it) {
                            LookingFor.MEN -> "Men"
                            LookingFor.WOMEN -> "Women"
                            LookingFor.EVERYONE -> "Everyone"
                        }
                    },
                    onSelected = { lookingFor = it }
                )
            }

            EditorSectionCard(
                title = "Media",
                subtitle = "Up to 6 photos total. Existing photos stay attached; new picks are added when you save."
            ) {
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select Photos")
                }
                Spacer(Modifier.height(12.dp))
                if (newImageUris.isNotEmpty()) {
                    Text(
                        "New photos: ${newImageUris.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(newImageUris) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(92.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    items(existingProfile?.resolvedImageUrls().orEmpty()) { url ->
                        AsyncImageDisplayUrl(
                            rawUrl = url,
                            contentDescription = null,
                            modifier = Modifier
                                .size(92.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

                EditorSectionCard(title = "Actions", subtitle = "Save changes or remove the profile entirely.") {
                    Button(
                        onClick = { onSave(name, bio, phone, country, gender, lookingFor, newImageUris) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading && name.isNotBlank() && bio.isNotBlank()
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (existingProfile == null) "Save Dating Profile" else "Update Dating Profile")
                        }
                    }
                    if (existingProfile != null) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Delete Dating Profile")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PreferenceChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                onClick = { onSelected(option) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                border = if (!isSelected) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                } else null
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlindDateScreen(
    datingState: DateEvaUiState,
    blindDateViewModel: BlindDateViewModel,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onAskAi: () -> Unit,
    onOpenInbox: (() -> Unit)?
) {
    val viewModel = blindDateViewModel
    val uiState by viewModel.uiState.collectAsState()
    val event by viewModel.events.collectAsState()
    val context = LocalContext.current

    var localBanner by remember { mutableStateOf<DateHubBannerMessage?>(null) }
    var joinStep by rememberSaveable { mutableStateOf(BlindDateJoinStep.DETAILS) }
    var joinDraft by remember { mutableStateOf<BlindDateJoinDraft?>(null) }
    var showJoinConfirm by remember { mutableStateOf(false) }
    var showRejoinConfirm by remember { mutableStateOf(false) }

    val joinFee = if (uiState.isStaffExempt) 0.0 else uiState.entryFeeUsd
    // Stripe Checkout is configured and authorized server-side. The callable
    // remains the source of truth for temporary provider availability.
    val providerCheckoutReady = true
    val isPaymentStep =
        uiState.status == BlindDateUserStatus.NotJoined && joinStep == BlindDateJoinStep.PAYMENT
    val joinCtaLabel = when {
        uiState.isLoading -> "Joining..."
        uiState.isPaymentCollectionPending -> "Payment Pending"
        uiState.isStaffExempt -> "Confirm & Join"
        else -> "Continue to Stripe ${formatCurrency(uiState.entryFeeUsd, "USD")}"
    }
    val rejoinCtaLabel = when {
        uiState.isLoading -> "Rejoining..."
        uiState.isPaymentCollectionPending -> "Payment Pending"
        uiState.isStaffExempt -> "Rejoin Blind Date"
        else -> "Continue to Stripe ${formatCurrency(uiState.entryFeeUsd, "USD")} to Rejoin"
    }

    LaunchedEffect(event) {
        when (val currentEvent = event) {
            is BlindDateEvent.NavigateToChat -> {
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra(SocialInboxNav.EXTRA_PUSH_CHAT_ID, currentEvent.chatId)
                    putExtra(SocialInboxNav.EXTRA_PUSH_OTHER_USER_ID, currentEvent.otherUserId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(intent)
                viewModel.onEventHandled()
            }
            is BlindDateEvent.OpenStripeCheckout -> {
                val opened = runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentEvent.url)))
                }.isSuccess
                localBanner = DateHubBannerMessage(
                    if (opened) currentEvent.message else "Checkout was created, but no browser is available to open it.",
                    if (opened) DateHubBannerTone.Info else DateHubBannerTone.Error
                )
                viewModel.onEventHandled()
            }
            is BlindDateEvent.ShowToast -> {
                localBanner = DateHubBannerMessage(currentEvent.message, currentEvent.tone)
                viewModel.onEventHandled()
            }
            null -> Unit
        }
    }

    LaunchedEffect(uiState.status) {
        if (uiState.status == BlindDateUserStatus.Active || uiState.status == BlindDateUserStatus.Matched) {
            joinStep = BlindDateJoinStep.DETAILS
            joinDraft = null
        }
    }

    if (showJoinConfirm) {
        AlertDialog(
            onDismissRequest = { showJoinConfirm = false },
            title = { Text("Confirm Blind Date Join") },
            text = {
                Text(
                    if (uiState.isStaffExempt) {
                        "Staff exemption detected. No fee will be charged for this join."
                    } else {
                        "A provider-side collection will start for this join. Dating access unlocks after payment confirmation. Continue?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showJoinConfirm = false
                        val draft = joinDraft ?: return@Button
                        viewModel.startProviderCollectionAndJoin(
                            mediaUris = draft.mediaUris,
                            bio = draft.bio,
                            gender = draft.gender,
                            lookingFor = draft.lookingFor,
                        )
                    },
                    enabled = !uiState.isLoading
                ) {
                    Text(if (uiState.isStaffExempt) "Confirm & Join" else "Confirm & Pay")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showRejoinConfirm) {
        AlertDialog(
            onDismissRequest = { showRejoinConfirm = false },
            title = { Text("Confirm Blind Date Rejoin") },
            text = {
                Text(
                    if (uiState.isStaffExempt) {
                        "No fee will be charged for this rejoin."
                    } else {
                        "A provider-side collection will start before you re-enter Blind Date. Continue?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRejoinConfirm = false
                        viewModel.rejoinLoop()
                    },
                    enabled = !uiState.isLoading
                ) {
                    Text(if (uiState.isStaffExempt) "Confirm & Rejoin" else "Confirm & Pay")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejoinConfirm = false }) { Text("Cancel") }
            }
        )
    }

    val blindDateScreenBrush = Brush.verticalGradient(colors = DateHubSurfaceGradient)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(blindDateScreenBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Blind Date", color = DateHubInk) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                shareDateHubInvite(
                                    context,
                                    if (isPaymentStep) ShareInviteTarget.BlindDatePayment else ShareInviteTarget.BlindDate
                                )
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share Blind Date invite")
                        }
                        IconButton(onClick = onAskAi) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Blind Date assistant")
                        }
                        if (!isPaymentStep) {
                            IconButton(onClick = onOpenProfile) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Optional: open Discover profile to edit shared details"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
            val banner = when {
                uiState.error != null -> uiState.error?.let {
                    DateHubBannerMessage(it, DateHubBannerTone.Error)
                }
                localBanner != null -> localBanner
                else -> datingState.bannerMessage
            }
            banner?.let {
                DateHubStatusBanner(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    message = it,
                    onDismiss = {
                        localBanner = null
                        viewModel.clearError()
                    }
                )
            }

                when {
                    uiState.isLoading && uiState.status == BlindDateUserStatus.NotJoined && joinDraft == null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.status == BlindDateUserStatus.AwaitingPayment -> {
                        EmptyStateCard(
                            modifier = Modifier.padding(20.dp),
                            title = "Awaiting payment",
                            body = uiState.paymentCollectionDetail
                                ?: "Your Blind Date entry fee is processing. Access unlocks after provider confirmation."
                        )
                    }
                    uiState.status == BlindDateUserStatus.Expired -> {
                        EmptyStateCard(
                            modifier = Modifier.padding(20.dp),
                            title = "Blind Date expired",
                            body = "Your Blind Date session expired. Rejoin from the matched screen when ready."
                        )
                    }
                    uiState.status == BlindDateUserStatus.NotJoined -> {
                        if (joinStep == BlindDateJoinStep.DETAILS) {
                            BlindDateProfileDetailsStep(
                                datingState = datingState,
                                onContinue = { bio, mediaUris, gender, lookingFor ->
                                    joinDraft = BlindDateJoinDraft(
                                        bio = bio,
                                        mediaUris = mediaUris,
                                        gender = gender,
                                        lookingFor = lookingFor,
                                    )
                                    joinStep = BlindDateJoinStep.PAYMENT
                                }
                            )
                        } else {
                            BlindDatePaymentStep(
                                draft = joinDraft,
                                fee = joinFee,
                                rawFee = uiState.entryFeeUsd,
                                isStaffExempt = uiState.isStaffExempt,
                                isProviderCollectionAvailable = providerCheckoutReady,
                                isLoading = uiState.isLoading,
                                isPaymentPending = uiState.isPaymentCollectionPending,
                                paymentDetail = uiState.paymentCollectionDetail,
                                ctaLabel = joinCtaLabel,
                                canSubmit = providerCheckoutReady && !uiState.isLoading && !uiState.isPaymentCollectionPending,
                                onBack = { joinStep = BlindDateJoinStep.DETAILS },
                                onSubmit = { showJoinConfirm = true }
                            )
                        }
                    }
                    uiState.status == BlindDateUserStatus.Active -> {
                        ActiveBlindDateContent(
                            uiState = uiState,
                            onInvite = viewModel::sendInvitation,
                            onAccept = viewModel::acceptInvitation,
                            onDecline = viewModel::declineInvitation,
                            onQueryChange = viewModel::onSearchQueryChanged,
                            onFilterChange = viewModel::onGenderFilterChanged,
                        )
                    }
                    uiState.status == BlindDateUserStatus.Matched -> {
                        MatchedBlindDateContent(
                            uiState = uiState,
                            rawFee = uiState.entryFeeUsd,
                            isProviderCollectionAvailable = providerCheckoutReady,
                            ctaLabel = rejoinCtaLabel,
                            canRejoin = providerCheckoutReady && !uiState.isLoading && !uiState.isPaymentCollectionPending,
                            onOpenChat = {
                                val chatId = uiState.matchedChatId
                                val otherUserId = uiState.matchedOtherUserId
                                if (!chatId.isNullOrBlank() && !otherUserId.isNullOrBlank()) {
                                    val intent = Intent(context, MainActivity::class.java).apply {
                                        putExtra(SocialInboxNav.EXTRA_PUSH_CHAT_ID, chatId)
                                        putExtra(SocialInboxNav.EXTRA_PUSH_OTHER_USER_ID, otherUserId)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    }
                                    context.startActivity(intent)
                                } else {
                                    onOpenInbox?.invoke()
                                        ?: Toast.makeText(context, "Open your inbox to continue the chat.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onRejoin = { showRejoinConfirm = true }
                        )
                    }
                    else -> {
                        EmptyStateCard(
                            modifier = Modifier.padding(20.dp),
                            title = "Blind Date unavailable",
                            body = "Blind Date is not active right now. Please check back later."
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlindDateProfileDetailsStep(
    datingState: DateEvaUiState,
    onContinue: (String, List<Uri>, Gender, LookingFor) -> Unit
) {
    val myProfile = datingState.myProfile
    var bio by remember(myProfile?.uid) { mutableStateOf(myProfile?.bio.orEmpty()) }
    LaunchedEffect(myProfile?.bio) {
        val latest = myProfile?.bio.orEmpty()
        if (latest.isNotBlank() && bio.isBlank()) bio = latest
    }
    var gender by remember(myProfile?.uid, datingState.userGender) {
        mutableStateOf(datingState.userGender ?: Gender.OTHER)
    }
    var lookingFor by remember(myProfile?.uid, datingState.lookingFor) {
        mutableStateOf(datingState.lookingFor ?: LookingFor.EVERYONE)
    }
    var mediaUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = BLIND_DATE_MAX_MEDIA),
        onResult = { uris -> mediaUris = uris.take(BLIND_DATE_MAX_MEDIA) }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.22f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Blind Date join",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "This screen is only for entering Blind Date. It is separate from your Discover profile. Entry opens after secure checkout is available and confirms your payment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("Step 1 of 2") }
                    )
                    Text("Profile Details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (myProfile != null) {
                            "Your Discover bio is filled in below — you can edit it for Blind Date. Add photos for Blind Date and confirm gender."
                        } else {
                            "Blind Date is separate from Discover. Set your bio, gender, and photos for Blind Date only."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Gender", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Gender.entries.forEach { option ->
                            FilterChip(
                                selected = gender == option,
                                onClick = { gender = option },
                                label = { Text(option.name.lowercase().replaceFirstChar(Char::titlecase)) }
                            )
                        }
                    }
                    Text("Looking for", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LookingFor.entries.forEach { option ->
                            FilterChip(
                                selected = lookingFor == option,
                                onClick = { lookingFor = option },
                                label = { Text(option.name.lowercase().replaceFirstChar(Char::titlecase)) }
                            )
                        }
                    }
                    Text(
                        "Add up to $BLIND_DATE_MAX_MEDIA photos or videos. This media stays with your Blind Date profile and does not change your Discover gallery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("Blind Date bio") },
                        placeholder = { Text("Give a quick read on your energy and what you're hoping for.") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp)
                    )
                    Button(
                        onClick = {
                            mediaPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Select Photos or Video")
                    }
                    if (mediaUris.isNotEmpty()) {
                        Text(
                            "Selected media: ${mediaUris.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(mediaUris) { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(92.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = { onContinue(bio.trim(), mediaUris, gender, lookingFor) },
                modifier = Modifier.fillMaxWidth(),
                enabled = bio.isNotBlank() && mediaUris.isNotEmpty()
            ) {
                Text("Continue to Payment")
            }
        }
    }
}

@Composable
private fun BlindDatePaymentStep(
    draft: BlindDateJoinDraft?,
    fee: Double,
    rawFee: Double,
    isStaffExempt: Boolean,
    isProviderCollectionAvailable: Boolean,
    isLoading: Boolean,
    isPaymentPending: Boolean,
    paymentDetail: String?,
    ctaLabel: String,
    canSubmit: Boolean,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AssistChip(onClick = {}, enabled = false, label = { Text("Step 2 of 2") })
                    Text("Payment Confirmation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Review your join before entering Blind Date.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            ProviderCollectionSummaryCard(
                fee = rawFee,
                isStaffExempt = isStaffExempt,
                isProviderCollectionAvailable = isProviderCollectionAvailable,
                isPaymentPending = isPaymentPending,
                paymentDetail = paymentDetail,
                title = "Join Summary"
            )
        }
        draft?.let { payload ->
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Blind Date details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Gender: ${payload.gender.name.lowercase().replaceFirstChar(Char::titlecase)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(payload.bio, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Media attached: ${payload.mediaUris.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), enabled = !isLoading) {
                    Text("Back")
                }
                Button(onClick = onSubmit, modifier = Modifier.weight(1f), enabled = canSubmit) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(ctaLabel)
                    }
                }
            }
        }
        if (!isStaffExempt && !isProviderCollectionAvailable) {
            item {
                DateHubStatusBanner(
                    message = DateHubBannerMessage(
                        "Secure checkout is being configured. You can review your details, but no payment or entry will start yet.",
                        DateHubBannerTone.Info
                    ),
                    onDismiss = null
                )
            }
        }
    }
}

@Composable
private fun ProviderCollectionSummaryCard(
    fee: Double,
    isStaffExempt: Boolean,
    isProviderCollectionAvailable: Boolean,
    isPaymentPending: Boolean,
    paymentDetail: String?,
    title: String
) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Wallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            SummaryRow(
                label = if (isStaffExempt) "Fee" else "Entry fee",
                value = if (isStaffExempt) "Staff exempt" else formatCurrency(fee, "USD")
            )
            SummaryRow(
                label = "Funding",
                value = when {
                    isStaffExempt -> "No provider collection"
                    isProviderCollectionAvailable -> "Provider-side collection"
                    else -> "Secure checkout unavailable"
                }
            )
            SummaryRow(
                label = "Access",
                value = when {
                    isStaffExempt -> "Unlocks immediately"
                    !isProviderCollectionAvailable -> "Unlocks after checkout is configured"
                    isPaymentPending -> "Pending provider confirmation"
                    else -> "Unlocks after provider confirms payment"
                }
            )
            paymentDetail?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ActiveBlindDateContent(
    uiState: BlindDateUiState,
    onInvite: (String) -> Unit,
    onAccept: (BlindDateInvitation) -> Unit,
    onDecline: (BlindDateInvitation) -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (Gender?) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val segments = listOf("Browse", "Invitations", "Timeline")

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("You are live in Blind Date", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Browse active profiles, manage invitations, and keep the full timeline in one place.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    segments.forEachIndexed { index, title ->
                        SegmentedButton(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = segments.size),
                            label = {
                                if (title == "Invitations" && uiState.receivedInvitations.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text(uiState.receivedInvitations.size.toString()) } }) {
                                        Text(title)
                                    }
                                } else {
                                    Text(title)
                                }
                            }
                        )
                    }
                }
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> BrowseProfilesPane(
                    profiles = uiState.filteredProfiles,
                    searchQuery = uiState.searchQuery,
                    genderFilter = uiState.genderFilter,
                    onQueryChange = onQueryChange,
                    onFilterChange = onFilterChange,
                    pendingInviteRecipientIds = uiState.pendingInviteRecipientIds,
                    onInvite = { profile ->
                        onInvite(profile.userId)
                    }
                )
                1 -> BlindDateInvitationsPane(
                    incoming = uiState.receivedInvitations,
                    outgoing = uiState.sentInvitations,
                    onAccept = onAccept,
                    onDecline = onDecline
                )
                else -> BlindDateTimelinePane(items = uiState.invitationTimeline)
            }
        }
    }
}

@Composable
private fun BrowseProfilesPane(
    profiles: List<BlindDateProfile>,
    searchQuery: String,
    genderFilter: Gender?,
    onQueryChange: (String) -> Unit,
    onFilterChange: (Gender?) -> Unit,
    pendingInviteRecipientIds: Set<String>,
    onInvite: (BlindDateProfile) -> Unit
) {
    var selectedProfile by remember { mutableStateOf<BlindDateProfile?>(null) }
    var inviteTarget by remember { mutableStateOf<BlindDateProfile?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search by name") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = genderFilter == null,
                    onClick = { onFilterChange(null) },
                    label = { Text("All") }
                )
                Gender.entries.forEach { gender ->
                    FilterChip(
                        selected = genderFilter == gender,
                        onClick = { onFilterChange(gender) },
                        label = { Text(gender.name.lowercase().replaceFirstChar(Char::titlecase)) }
                    )
                }
            }
        }

        if (profiles.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No matching profiles",
                    body = if (searchQuery.isBlank() && genderFilter == null) {
                        "No one is active in Blind Date right now."
                    } else {
                        "Try a broader search or clear your gender filter."
                    }
                )
            }
        } else {
            items(profiles, key = { it.userId }) { profile ->
                BlindDateProfileCard(
                    profile = profile,
                    isInviteInFlight = profile.userId in pendingInviteRecipientIds,
                    onOpen = { selectedProfile = profile },
                    onInvite = { inviteTarget = profile }
                )
            }
        }
    }

    selectedProfile?.let { profile ->
        BlindDateProfileDetailDialog(
            profile = profile,
            onDismiss = { selectedProfile = null },
            onInvite = {
                selectedProfile = null
                inviteTarget = profile
            }
        )
    }

    inviteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { inviteTarget = null },
            title = { Text("Send Blind Date Invitation") },
            text = { Text("This is a double-consent flow. ${target.name} must accept before a chat opens.") },
            confirmButton = {
                Button(onClick = {
                    onInvite(target)
                    inviteTarget = null
                }, enabled = target.userId !in pendingInviteRecipientIds) {
                    Text(if (target.userId in pendingInviteRecipientIds) "Sending..." else "Send Invitation")
                }
            },
            dismissButton = {
                TextButton(onClick = { inviteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlindDateProfileCard(
    profile: BlindDateProfile,
    isInviteInFlight: Boolean,
    onOpen: () -> Unit,
    onInvite: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val mediaItems = remember(profile.media, profile.mediaUrls) {
        profile.media.ifEmpty { profile.mediaUrls }
    }
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (mediaItems.isNotEmpty()) {
                val pagerState = rememberPagerState(pageCount = { mediaItems.size })
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClickLabel = "Open ${profile.name.ifBlank { "profile" }} details",
                            onClick = onOpen
                        )
                ) {
                    HorizontalPager(state = pagerState) { page ->
                        AsyncImageDisplayUrl(
                            rawUrl = mediaItems[page],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (mediaItems.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(mediaItems.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (pagerState.currentPage == index) Color.White else Color.White.copy(alpha = 0.4f)
                                        )
                                )
                            }
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(profile.name.ifBlank { "Anonymous" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(profile.bio.ifBlank { "No bio yet." }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(
                    profile.gender.ifBlank { "Other" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Text("View")
                }
                Button(
                    onClick = onInvite,
                    modifier = Modifier.weight(1f),
                    enabled = !isInviteInFlight
                ) {
                    Icon(Icons.Default.PersonAddAlt1, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isInviteInFlight) "Sending..." else "Invite")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlindDateProfileDetailDialog(
    profile: BlindDateProfile,
    onDismiss: () -> Unit,
    onInvite: () -> Unit
) {
    val mediaItems = remember(profile.media, profile.mediaUrls) {
        profile.media.ifEmpty { profile.mediaUrls }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(profile.name.ifBlank { "Blind Date Profile" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (mediaItems.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { mediaItems.size })
                    HorizontalPager(state = pagerState, modifier = Modifier.height(220.dp)) { page ->
                        AsyncImageDisplayUrl(
                            rawUrl = mediaItems[page],
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Text(profile.bio.ifBlank { "No bio yet." })
                Text(
                    profile.gender.ifBlank { "Other" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(onClick = onInvite) { Text("Invite") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun BlindDateInvitationsPane(
    incoming: List<BlindDateInvitation>,
    outgoing: List<BlindDateInvitation>,
    onAccept: (BlindDateInvitation) -> Unit,
    onDecline: (BlindDateInvitation) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Incoming", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (incoming.isEmpty()) {
            item {
                EmptyStateCard(title = "No incoming invitations", body = "When someone invites you, their card will appear here.")
            }
        } else {
            items(incoming, key = { "incoming-${it.senderId}" }) { invitation ->
                InvitationCard(
                    invitation = invitation,
                    isIncoming = true,
                    onAccept = { onAccept(invitation) },
                    onDecline = { onDecline(invitation) }
                )
            }
        }

        item {
            Text("Outgoing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (outgoing.isEmpty()) {
            item {
                EmptyStateCard(title = "No outgoing invitations", body = "Invitations you send will stay here while they are pending or after they resolve.")
            }
        } else {
            items(outgoing, key = { "outgoing-${it.recipientId}" }) { invitation ->
                InvitationCard(
                    invitation = invitation,
                    isIncoming = false,
                    onAccept = {},
                    onDecline = {}
                )
            }
        }
    }
}

@Composable
private fun InvitationCard(
    invitation: BlindDateInvitation,
    isIncoming: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val title = if (isIncoming) invitation.senderName.ifBlank { "Unknown user" } else invitation.recipientName.ifBlank { "Unknown user" }
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusChip(status = invitation.status)
            }
            Text(
                if (isIncoming) {
                    "Incoming invitation"
                } else {
                    "Outgoing invitation"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatTimelineMoment(invitation.updatedAt ?: invitation.respondedAt ?: invitation.sentAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (isIncoming) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onAccept, modifier = Modifier.weight(1f)) {
                        Text("Accept")
                    }
                    OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) {
                        Text("Decline")
                    }
                }
            }
        }
    }
}

@Composable
private fun BlindDateTimelinePane(items: List<BlindDateTimelineItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (items.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No timeline yet",
                    body = "Your invitation history will build here after you start sending or receiving invites."
                )
            }
        } else {
            items(items, key = { it.id }) { item ->
                TimelineCard(item = item)
            }
        }
    }
}

@Composable
private fun TimelineCard(item: BlindDateTimelineItem) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.otherUserName.ifBlank { "Unknown user" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                DirectionChip(direction = item.direction)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(status = item.status)
                AssistChip(onClick = {}, enabled = false, label = { Text(formatTimelineMoment(item.updatedAt ?: item.sentAt)) })
            }
        }
    }
}

@Composable
private fun DirectionChip(direction: BlindDateInviteDirection) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(if (direction == BlindDateInviteDirection.RECEIVED) "Incoming" else "Outgoing") }
    )
}

@Composable
private fun StatusChip(status: String) {
    val normalized = status.trim().lowercase(Locale.ROOT)
    val containerColor = when (normalized) {
        "pending" -> Color(0xFFFFF4DE)
        "accepted", "matched" -> Color(0xFFE5F0FF)
        "declined" -> Color(0xFFFDECEC)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (normalized) {
        "pending" -> Color(0xFF8F5A00)
        "accepted", "matched" -> Color(0xFF00539F)
        "declined" -> Color(0xFFB42318)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (normalized) {
        "pending" -> Icons.Default.Info
        "accepted", "matched" -> Icons.Default.CheckCircle
        "declined" -> Icons.Default.Close
        else -> Icons.Default.Info
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = normalized.replaceFirstChar(Char::titlecase),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MatchedBlindDateContent(
    uiState: BlindDateUiState,
    rawFee: Double,
    isProviderCollectionAvailable: Boolean,
    ctaLabel: String,
    canRejoin: Boolean,
    onOpenChat: () -> Unit,
    onRejoin: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8FFF1))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Celebration, contentDescription = null, tint = Color(0xFF0B8F55))
                        Text("It's a match", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (!uiState.matchedOtherUserName.isNullOrBlank()) {
                            "You matched with ${uiState.matchedOtherUserName}. Your chat handoff is ready."
                        } else {
                            "You matched successfully. Your chat handoff is ready."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF135541)
                    )
                    Button(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.matchedChatId.isNullOrBlank()) "Open Inbox" else "Open Match Chat")
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Rejoin Blind Date", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "You cannot be active in Blind Date while matched. Rejoining moves you back into the pool after confirmation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ProviderCollectionSummaryCard(
                        fee = rawFee,
                        isStaffExempt = uiState.isStaffExempt,
                        isProviderCollectionAvailable = isProviderCollectionAvailable,
                        isPaymentPending = uiState.isPaymentCollectionPending,
                        paymentDetail = uiState.paymentCollectionDetail,
                        title = "Rejoin Summary"
                    )
                    Button(onClick = onRejoin, modifier = Modifier.fillMaxWidth(), enabled = canRejoin) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(ctaLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DateHubStatusBanner(
    message: DateHubBannerMessage,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val containerColor = when (message.tone) {
        DateHubBannerTone.Info -> Color(0xFFEAF3FF)
        DateHubBannerTone.Success -> Color(0xFFE7F8ED)
        DateHubBannerTone.Error -> Color(0xFFFFECEB)
    }
    val contentColor = when (message.tone) {
        DateHubBannerTone.Info -> Color(0xFF1E4E8C)
        DateHubBannerTone.Success -> Color(0xFF17603A)
        DateHubBannerTone.Error -> Color(0xFFB42318)
    }
    val icon = when (message.tone) {
        DateHubBannerTone.Info -> Icons.Default.VerifiedUser
        DateHubBannerTone.Success -> Icons.Default.CheckCircle
        DateHubBannerTone.Error -> Icons.Default.Warning
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(20.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = when (message.tone) {
                    DateHubBannerTone.Info -> "Information"
                    DateHubBannerTone.Success -> "Success"
                    DateHubBannerTone.Error -> "Error"
                },
                tint = contentColor
            )
            Text(
                text = message.text,
                modifier = Modifier.weight(1f),
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium
            )
            onDismiss?.let { dismiss ->
                IconButton(onClick = dismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss banner", tint = contentColor)
                }
            }
        }
    }
}

@Composable
private fun AgeVerificationDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var age by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Age Verification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("To enter Discover or Blind Date, confirm that you are 18 or older.")
                OutlinedTextField(
                    value = age,
                    onValueChange = { input -> age = input.filter(Char::isDigit).take(2) },
                    label = { Text("Age") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(age.toIntOrNull() ?: 0) }) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun UnderAgeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("18+ Required") },
        text = { Text("You must be 18 or older to access Discover or Blind Date.") },
        confirmButton = {
            Button(onClick = onDismiss) { Text("OK") }
        }
    )
}

private fun formatCurrency(amount: Double, currencySymbol: String): String {
    return "$currencySymbol${String.format(Locale.US, "%.2f", amount)}"
}

private fun formatTimelineMoment(date: Date?): String {
    if (date == null) return "Just now"
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)
}

private enum class ShareInviteTarget {
    DatingHub,
    DatingLoop,
    DatingProfile,
    BlindDate,
    BlindDatePayment,
}

private fun shareDateHubInvite(context: Context, target: ShareInviteTarget) {
    val pathSteps =
        "Install the Volunteers App, sign in, then open Community (or the main menu) -> Dating Hub -> Blind Date."
    val message = when (target) {
        ShareInviteTarget.DatingHub -> buildString {
            append("Join me in Dating Hub on the Volunteers App.\n")
            append(pathSteps)
            append("\nFrom Dating Hub you can open Discover (browse + chat invites) or Blind Date (curated invites and private chat).")
        }
        ShareInviteTarget.DatingLoop -> buildString {
            append("Join me in Discover on the Volunteers App.\n")
            append("Open Community -> Dating Hub -> Discover to browse profiles and send respectful chat invites.")
        }
        ShareInviteTarget.DatingProfile -> buildString {
            append("I'm editing my Discover profile in the Volunteers App.\n")
            append("Open Community -> Dating Hub -> Discover, then use the profile icon to add photos and preferences.")
        }
        ShareInviteTarget.BlindDate -> buildString {
            append("Join me on Blind Date in the Volunteers App.\n")
            append(pathSteps)
            append("\nYou'll need 18+ verification and a Discover profile before Blind Date (same gate as in the app).")
        }
        ShareInviteTarget.BlindDatePayment -> buildString {
            append("I'm completing Blind Date join (step 2 of 2) in the Volunteers App.\n")
            append(pathSteps)
            append("\nShare this invite so friends can find the same path.")
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Join me on Volunteers App")
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, "Share invite"))
}
