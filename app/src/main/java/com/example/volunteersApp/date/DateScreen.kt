package com.example.volunteersApp.date

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.chat.ChatActivity
import com.example.volunteersApp.ui.shared.AiResponseDialog
import com.example.volunteersApp.wallet.WalletViewModel
import com.google.firebase.appcheck.FirebaseAppCheck


class BlindDateViewModelFactory(private val walletViewModel: WalletViewModel) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BlindDateViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BlindDateViewModel(walletViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

/**
 * Modernized Dating Discovery Screen (Dating Loop).
 * Features cinematic card layouts, immersive onboarding, and premium Material 3 styling.
 */
@Composable
fun DateEvaScreen(
    viewModel: DateEvaViewModel,
    // Add the shared VertexViewModel
    vertexViewModel: VertexViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // --- AI DIALOG: This dialog is shared across all sub-screens ---
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var showAiResponseDialog by remember { mutableStateOf(false) }

    // When the AI generates a response, show the dialog
    LaunchedEffect(aiResponse) {
        if (aiResponse != null) {
            showAiResponseDialog = true
        }
    }

    if (showAiResponseDialog) {
        AiResponseDialog(
            generatedText = aiResponse.orEmpty(),
            onDismiss = {
                showAiResponseDialog = false
                vertexViewModel.clearResponse()
            }
        )
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(500)),
        exit = fadeOut()
    ) {
        when (uiState.currentStep) {
            OnboardingStep.MAIN_CONTENT -> DatingMainPage(
                uiState = uiState,
                currentUserId = viewModel.getCurrentUserId(),
                onStartPostFlow = { viewModel.startPostFlow() },
                // *** NEW: Add handler for starting the blind date flow ***
                onStartBlindDateFlow = { viewModel.startBlindDateFlow() },
                onDeleteProfile = { viewModel.deleteProfile() },
                onUpdateBio = { viewModel.updateProfileBio(it) },
                onChatClicked = { profile ->
                    viewModel.sendChatInvitation(profile) { _, message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                },
                // Pass the AI generation function
                onGenerate = { prompt ->
                    FirebaseAppCheck.getInstance().getToken(false)
                        .addOnSuccessListener { vertexViewModel.generate(prompt) }
                        .addOnFailureListener { Log.e("AppCheck", "Token not ready", it) }
                }
            )
            OnboardingStep.CREATE_PROFILE -> CreateProfileScreen(
                uiState = uiState,
                onCreateProfile = { name, bio, phone, uris -> viewModel.createProfile(name, bio, phone, uris) },
                onCancel = { viewModel.navigateToMain() },
                // Pass the AI generation function to the profile creation screen
                onGenerateBio = { prompt ->
                    FirebaseAppCheck.getInstance().getToken(false)
                        .addOnSuccessListener { vertexViewModel.generate(prompt) }
                        .addOnFailureListener { Log.e("AppCheck", "Token not ready", it) }
                }
            )
            // *** NEW: Add a case for the Blind Date landing screen ***
            OnboardingStep.BLIND_DATE_LANDING -> BlindDateLandingScreen(
                onBack = { viewModel.navigateToMain() },
                userGender = uiState.userGender,
                walletViewModel = viewModel.walletViewModel
            )
            // Other onboarding steps remain unchanged
            OnboardingStep.AGE_CHECK -> AgeCheckScreen(
                onAgeEntered = { viewModel.onAgeEntered(it) },
                onCancel = { viewModel.navigateToMain() })
            OnboardingStep.AGE_DECLINED -> AgeDeclinedScreen(
                onGoBack = { viewModel.navigateToMain() })
            OnboardingStep.PREFERENCE_SELECTION -> PreferenceScreen(
                onPreferencesSelected = { gender, lookingFor ->
                    viewModel.onPreferencesSelected(gender, lookingFor) },
                onCancel = { viewModel.navigateToMain() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatingMainPage(
    uiState: DateEvaUiState,
    currentUserId: String?,
    onStartPostFlow: () -> Unit,
    // *** NEW: Add a callback for the blind date button ***
    onStartBlindDateFlow: () -> Unit,
    onDeleteProfile: () -> Unit,
    onUpdateBio: (String) -> Unit,
    onChatClicked: (DatingProfile) -> Unit,
    // New parameter to handle AI generation
    onGenerate: (String) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var tempBio by remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Dating Loop", fontWeight = FontWeight.ExtraBold)
                        Text("Connect with the community", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    // *** NEW: Add the Blind Date Button here ***
                    Button(
                        onClick = onStartBlindDateFlow,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.TheaterComedy, contentDescription = "Blind Date")
                        Spacer(Modifier.width(4.dp))
                        Text("Blind Date")
                    }

                    // --- AI DATING CONCIERGE BUTTON ---
                    IconButton(onClick = {
                        val prompt = """
                            Act as a friendly dating concierge for the "Dating Loop".
                            My profile name is ${uiState.myProfile?.name ?: "a user"}.
                            Suggest one fun and creative idea for a first date that can be done through a volunteer event.
                        """.trimIndent()
                        onGenerate(prompt)
                    }) {
                        Icon(Icons.Default.AutoAwesome, "AI Dating Concierge")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onStartPostFlow,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(if (uiState.myProfile == null) Icons.Default.Add else Icons.Default.Edit, null) },
                text = { Text(if (uiState.myProfile == null) "Join Loop" else "My Profile") }
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            if (uiState.allProfiles.isEmpty() && !uiState.isLoading) {
                EmptyDatingPlaceholder(onJoin = onStartPostFlow)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.allProfiles, key = { it.uid }) { profile ->
                        DatingProfileCard(
                            profile = profile,
                            isOwnProfile = profile.uid == currentUserId,
                            onEditClicked = {
                                tempBio = profile.bio
                                showEditDialog = true
                            },
                            onDeleteClicked = { showDeleteDialog = true },
                            onChatClicked = { onChatClicked(profile) },
                            // Pass the AI generation function down to the card
                            onGenerate = onGenerate,
                            myProfile = uiState.myProfile
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
            if (uiState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }

    // --- AI BIO GENERATION IN EDIT DIALOG ---
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Update About Me") },
            text = {
                OutlinedTextField(
                    value = tempBio,
                    onValueChange = { tempBio = it },
                    label = { Text("Bio") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            val prompt = "Rewrite this bio to be more fun and engaging, but keep the core message: '$tempBio'"
                            onGenerate(prompt)
                        }) {
                            Icon(Icons.Default.AutoAwesome, "Regenerate Bio with AI")
                        }
                    }
                )
            },
            confirmButton = { Button(onClick = { onUpdateBio(tempBio); showEditDialog = false }) { Text("Update") } },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Profile") },
            text = { Text("Are you sure you want to remove yourself from the Dating Loop? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProfile()
                        showDeleteDialog = false
                        Toast.makeText(context, "Profile removed", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

/**
 * UPDATED: Composable for the Blind Date Screen.
 * This is the main entry point for the blind date feature.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlindDateLandingScreen(
    onBack: () -> Unit,
    walletViewModel: WalletViewModel,
    userGender: Gender?
) {
    val viewModel: BlindDateViewModel = viewModel(factory = BlindDateViewModelFactory(walletViewModel))
    val uiState by viewModel.uiState.collectAsState()
    val event by viewModel.events.collectAsState()
    val context = LocalContext.current

    var showJoinDialog by remember { mutableStateOf(false) }
    var paymentConfirmationData by remember { mutableStateOf<Pair<List<Uri>, String>?>(null) }

    if (showJoinDialog) {
        JoinBlindDateDialog(
            onDismiss = { showJoinDialog = false },
            onConfirm = { mediaUris, bio ->
                showJoinDialog = false
                paymentConfirmationData = Pair(mediaUris, bio)
            }
        )
    }

    paymentConfirmationData?.let { (mediaUris, bio) ->
        ConfirmPaymentDialog(
            onDismiss = { paymentConfirmationData = null },
            onConfirm = {
                paymentConfirmationData = null
                viewModel.payFromWalletAndJoin(mediaUris, bio, userGender ?: Gender.OTHER)
            }
        )
    }

    LaunchedEffect(event) {
        when (val currentEvent = event) {
            is BlindDateEvent.NavigateToChat -> {
                val intent = Intent(context, ChatActivity::class.java).apply {
                    putExtra("CHAT_ID", currentEvent.chatId)
                    putExtra("OTHER_USER_ID", currentEvent.otherUserId)
                    putExtra("IS_NEW_BLIND_DATE", true)
                }
                context.startActivity(intent)
                viewModel.onEventHandled()
            }
            is BlindDateEvent.ShowToast -> {
                Toast.makeText(context, currentEvent.message, Toast.LENGTH_LONG).show()
                viewModel.onEventHandled()
            }
            null -> { /* No event */ }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blind Date") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (uiState.status) {
                    BlindDateUserStatus.NotJoined -> NotJoinedContent(onJoin = { showJoinDialog = true })
                    BlindDateUserStatus.Active -> ActiveBlindDateContent(
                        uiState = uiState,
                        onInvite = viewModel::sendInvitation,
                        onAccept = viewModel::acceptInvitation,
                        onQueryChange = viewModel::onSearchQueryChanged,
                        onFilterChange = viewModel::onGenderFilterChanged
                    )
                    BlindDateUserStatus.Matched -> MatchedContent(onBack = onBack)
                    BlindDateUserStatus.Expired -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("This event has ended.", textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}






@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveBlindDateContent(
    uiState: BlindDateUiState,
    onInvite: (String) -> Unit,
    onAccept: (BlindDateInvitation) -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (Gender?) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Browse", "Invitations")

    Column {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                    icon = {
                        if (title == "Invitations") {
                            BadgedBox(
                                badge = {
                                    if (uiState.receivedInvitations.isNotEmpty()) {
                                        Badge { Text("${uiState.receivedInvitations.size}") }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Mail, contentDescription = "Invitations")
                            }
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "Browse")
                        }
                    }
                )
            }
        }
        when (selectedTab) {
            0 -> BrowseProfilesScreen(
                profiles = uiState.filteredProfiles,
                searchQuery = uiState.searchQuery,
                genderFilter = uiState.genderFilter,
                onQueryChange = onQueryChange,
                onFilterChange = onFilterChange,
                onInvite = onInvite
            )
            1 -> ReceivedInvitationsScreen(invitations = uiState.receivedInvitations, onAccept = onAccept)
        }
    }
}

// --- Helper Composables for BlindDateLandingScreen ---

@Composable
fun NotJoinedContent(onJoin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.TheaterComedy,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("Welcome to the Blind Date Stage!", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text("Post your profile to be seen by others. If you match, you're off to a private chat!", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onJoin, modifier = Modifier.fillMaxWidth()) {
            Text("Join for $10")
        }
    }
}

@Composable
fun MatchedContent(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Celebration, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color(0xFF4CAF50))
        Spacer(Modifier.height(16.dp))
        Text("It's a Match!", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text("You have been matched and moved to a private chat. Good luck!", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Go to My Chats")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseProfilesScreen(
    profiles: List<BlindDateProfile>,
    searchQuery: String,
    genderFilter: Gender?,
    onQueryChange: (String) -> Unit,
    onFilterChange: (Gender?) -> Unit,
    onInvite: (userId: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // --- Search and Filter UI ---
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by name...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                shape = RoundedCornerShape(28.dp),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                // "All" Filter Chip
                FilterChip(
                    selected = genderFilter == null,
                    onClick = { onFilterChange(null) },
                    label = { Text("All") },
                    leadingIcon = if (genderFilter == null) {
                        { Icon(Icons.Default.Done, contentDescription = "Selected") }
                    } else {
                        null
                    }
                )
                // Gender-specific Filter Chips
                // Using values().asList() to avoid potential recomposition issues
                Gender.values().asList().forEach { gender ->
                    FilterChip(
                        selected = genderFilter == gender,
                        onClick = { onFilterChange(gender) },
                        label = { Text(gender.name.replaceFirstChar { it.uppercase() }) },
                        leadingIcon = if (genderFilter == gender) {
                            { Icon(Icons.Default.Done, contentDescription = "Selected") }
                        } else {
                            null
                        }
                    )
                }
            }
        }

        // --- Profiles List ---
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty() || genderFilter != null) {
                        "No matching profiles found for your search."
                    } else {
                        "No one is on the Blind Date stage right now. Check back soon!"
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(profiles, key = { it.userId }) { profile ->
                    // THE FIX: The call to BlindDateProfileCard is now correct.
                    // The extra trailing lambda has been removed.
                    BlindDateProfileCard(
                        profile = profile,
                        onInvite = { onInvite(profile.userId) }
                    )
                }
            }
        }
    }
}


/**
 * THE FIX: A new card specifically for `BlindDateProfile`.
 * It correctly uses `profile.media` to display images.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BlindDateProfileCard(
    profile: BlindDateProfile,
    onInvite: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { profile.media.size })

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Image Pager
            if (profile.media.isNotEmpty()) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)) {
                    HorizontalPager(state = pagerState) { page ->
                        AsyncImage(
                            model = profile.media[page], // CORRECT: Using the 'media' field
                            contentDescription = "Profile media",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    // Pager Indicators
                    if (profile.media.size > 1) {
                        Row(
                            Modifier
                                .height(50.dp)
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(profile.media.size) { iteration ->
                                val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else Color.LightGray
                                Box(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .size(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // User Info
            Column(modifier = Modifier.padding(16.dp)) {
                Text(profile.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(profile.bio, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onInvite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Mail, contentDescription = "Invite")
                    Spacer(Modifier.width(8.dp))
                    Text("Send Invitation")
                }
            }
        }
    }
}
@Composable
fun ReceivedInvitationsScreen(invitations: List<BlindDateInvitation>, onAccept: (BlindDateInvitation) -> Unit) {
    if (invitations.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("You have no pending invitations.")
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(invitations) { invitation ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${invitation.senderName} sent you an invitation!", modifier = Modifier.weight(1f))
                    Button(onClick = { onAccept(invitation) }) {
                        Text("Accept")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DatingProfileCard(
    profile: DatingProfile,
    isOwnProfile: Boolean,
    onEditClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onChatClicked: () -> Unit,
    onGenerate: (String) -> Unit,
    myProfile: DatingProfile?
) {
    var isExpanded by remember { mutableStateOf(false) }
    val cardElevation by animateDpAsState(if (isExpanded) 8.dp else 2.dp, label = "cardElevation")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(24.dp),

        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
    ) {
        Column {
            val pagerState = rememberPagerState(pageCount = { profile.imageUrls.size.coerceAtLeast(1) })
            Box(
                modifier = Modifier
                    .height(if (isExpanded) 450.dp else 300.dp) // Expandable height
                    .fillMaxWidth()
            ) {
                if (profile.imageUrls.isNotEmpty()) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        AsyncImage(
                            model = profile.imageUrls[page],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(0.7f)
                                ), startY = 300f
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Text(
                        profile.name,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "${profile.gender} • Seeking ${profile.lookingFor}",
                        color = Color.White.copy(0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (profile.country.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "Location",
                                tint = Color.White.copy(0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                profile.country,
                                color = Color.White.copy(0.8f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                if (pagerState.pageCount > 1) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(pagerState.pageCount) { iteration ->
                            val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .size(8.dp)
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("About Me", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(profile.bio, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))

                    if (isOwnProfile) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onEditClicked,
                                modifier = Modifier.weight(1f)
                            ) { Text("Edit Bio") }
                            OutlinedButton(
                                onClick = onDeleteClicked,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Delete Profile") }
                        }
                    } else {
                        // --- AI-Powered Action Buttons for other profiles ---
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Standard chat button
                            Button(onClick = onChatClicked, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Chat, "Chat", Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Start a Conversation")
                            }

                            // AI Icebreaker Button
                            OutlinedButton(
                                onClick = {
                                    val prompt = """
                                        Generate a fun, one-sentence icebreaker to send to ${profile.name}.
                                        My profile says I'm interested in volunteering.
                                        Their profile bio is: "${profile.bio}".
                                        Don't ask a question, make a fun statement.
                                    """.trimIndent()
                                    onGenerate(prompt)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AutoAwesome, "AI Icebreaker", Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Generate Icebreaker")
                            }

                            // AI Image Generation Button
                            OutlinedButton(
                                onClick = {
                                    val prompt = """
                                        The user wants an image to break the ice with ${profile.name}.
                                        Their interests seem to be related to helping the community, based on their bio: "${myProfile?.bio ?: "...''"}.
                                        My interests are related to my bio: "${profile.bio}".
                                        Generate a cool, artistic, and slightly funny image of two cartoon animals (like a fox and a raccoon) happily planting a tree together, representing our shared interest in community. The style should be vibrant and modern.
                                        This is an image generation request.
                                    """.trimIndent()
                                    onGenerate(prompt)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Image, "AI Image", Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Create Shared Interest Image")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyDatingPlaceholder(onJoin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.FavoriteBorder,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "It's a bit quiet here...",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            "Be the first to join the dating loop and find your match!",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onJoin) { Text("Join the Loop") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProfileScreen(
    uiState: DateEvaUiState,
    onCreateProfile: (name: String, bio: String, phone: String, imageUris: List<Uri>) -> Unit,onCancel: () -> Unit,
    // New parameter to handle AI bio generation
    onGenerateBio: (String) -> Unit
) {
    var name by remember { mutableStateOf(uiState.myProfile?.name ?: "") }
    // --- THIS IS THE FIX: Corrected 'mutableStateOf' ---
    var bio by remember { mutableStateOf(uiState.myProfile?.bio ?: "") }
    var phone by remember { mutableStateOf(uiState.myProfile?.phone ?: "") }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val existingImages = uiState.myProfile?.imageUrls ?: emptyList()

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris -> imageUris = uris }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.myProfile != null) "Edit Profile" else "Create Profile") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("About Me") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                placeholder = { Text("Tell everyone a bit about yourself...") },
                // --- AI BIO GENERATION TRAILING ICON ---
                trailingIcon = {
                    IconButton(onClick = {
                        val prompt = "Write a fun and friendly dating profile bio for someone named $name who is passionate about volunteering and community work. Make it 2-3 sentences long."
                        onGenerateBio(prompt)
                    }) {
                        Icon(Icons.Default.AutoAwesome, "Generate Bio with AI")
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            Spacer(Modifier.height(24.dp))

            Button(onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Default.AddPhotoAlternate, null)
                Spacer(Modifier.width(8.dp))
                Text("Select Photos (${imageUris.size} new selected)")
            }
            Spacer(Modifier.height(16.dp))

            // The LazyRow should be inside a Box with a fixed height to prevent layout issues inside a Column
            Box(modifier = Modifier.heightIn(min = 100.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (imageUris.isNotEmpty()) {
                        items(imageUris) { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = "New image",
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else if (existingImages.isNotEmpty()) {
                        items(existingImages) { url: String ->
                            AsyncImage(
                                model = url,
                                contentDescription = "Existing image",
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }


            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    if (name.isBlank() || bio.isBlank()) {
                        Toast.makeText(context, "Please fill out all fields.", Toast.LENGTH_SHORT).show()
                    } else {
                        onCreateProfile(name, bio, phone, imageUris)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Save Profile")
                }
            }
            uiState.profileCreationError?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(20.dp)) // Add padding at the bottom
        }
    }
}



// Add this new Composable to your DateScreen.kt file

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinBlindDateDialog(
    onDismiss: () -> Unit,
    onConfirm: (mediaUris: List<Uri>, bio: String) -> Unit
) {
    var bio by remember { mutableStateOf("") }
    var selectedMediaUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // Image picker launcher
    val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3),
        onResult = { uris -> selectedMediaUris = uris }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Your Blind Date Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Bio input
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Short Bio for the Date") },
                    placeholder = { Text("What's the vibe?") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Media picker
                Text("Add up to 3 photos/videos", style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = {
                        multiplePhotoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select Media")
                }

                // Display selected media previews
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectedMediaUris) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedMediaUris, bio) },
                // Disable button until bio and at least one image is added
                enabled = bio.isNotBlank() && selectedMediaUris.isNotEmpty()
            ) {
                // --- THIS IS THE MODIFIED TEXT ---
                Text("Proceed to Payment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// In DateScreen.kt, add this new composable.

@Composable
fun ConfirmPaymentDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Wallet, contentDescription = "Wallet Icon") },
        title = { Text("Confirm Payment") },
        text = { Text("A fee of $10 will be deducted from your wallet to join the Blind Date. Do you wish to proceed?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Confirm & Pay")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@Composable
fun AgeCheckScreen(onAgeEntered: (Int) -> Unit, onCancel: () -> Unit) {
    var age by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Age Verification") },
        text = {
            Column {
                Text("To access the Dating Loop, please confirm you are 18 or older.")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = age,
                    onValueChange = { if (it.length <= 2) age = it.filter { c -> c.isDigit() } },
                    label = { Text("Your Age") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAgeEntered(age.toIntOrNull() ?: 0) }) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}

@Composable
fun AgeDeclinedScreen(onGoBack: () -> Unit) {
    AlertDialog(
        onDismissRequest = onGoBack,
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Access Denied") },
        text = { Text("Sorry, you must be 18 or older to access the Dating Loop.") },
        confirmButton = { Button(onClick = onGoBack) { Text("Go Back") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferenceScreen(onPreferencesSelected: (Gender, LookingFor) -> Unit, onCancel: () -> Unit) {
    var gender by remember { mutableStateOf(Gender.OTHER) }
    var lookingFor by remember { mutableStateOf(LookingFor.EVERYONE) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Set Your Preferences") },
        text = {
            Column {
                var genderExpanded by remember { mutableStateOf(false) }
                var lookingForExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(expanded = genderExpanded, onExpandedChange = { genderExpanded = it }) {
                    OutlinedTextField(
                        value = gender.name, onValueChange = {},
                        label = { Text("I am a...") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = genderExpanded, onDismissRequest = { genderExpanded = false }) {
                        Gender.values().forEach { g ->
                            DropdownMenuItem(text = { Text(g.name) }, onClick = { gender = g; genderExpanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                ExposedDropdownMenuBox(expanded = lookingForExpanded, onExpandedChange = { lookingForExpanded = it }) {
                    OutlinedTextField(
                        value = lookingFor.name, onValueChange = {},
                        label = { Text("I am looking for...") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lookingForExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = lookingForExpanded, onDismissRequest = { lookingForExpanded = false }) {
                        LookingFor.values().forEach { lf ->
                            DropdownMenuItem(text = { Text(lf.name) }, onClick = { lookingFor = lf; lookingForExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPreferencesSelected(gender, lookingFor) }) { Text("Next") }
        }
    )
}
