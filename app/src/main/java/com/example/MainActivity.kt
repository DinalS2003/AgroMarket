package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.data.models.AppMode
import com.example.data.models.ListingItem
import com.example.data.models.PayHerePaymentRequest
import com.example.ui.components.AgroBottomNavigation
import com.example.ui.components.AgroTopBar
import com.example.ui.components.OfflineBanner
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.chat.MessagesScreen
import com.example.ui.screens.farmer.AddEditListingScreen
import com.example.ui.screens.farmer.FarmerDashboardScreen
import com.example.ui.screens.farmer.MyListingsScreen
import com.example.ui.screens.listing.ListingDetailScreen
import com.example.ui.screens.notifications.NotificationsScreen
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.orders.OrderDetailScreen
import com.example.ui.screens.orders.OrdersListScreen
import com.example.ui.screens.orders.PlaceOrderScreen
import com.example.ui.screens.marketplace.MarketplaceScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val app get() = AgroMarketApp.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()

                // State from SessionManager
                val authToken by app.sessionManager.authTokenFlow.collectAsState(initial = null)
                val userId by app.sessionManager.userIdFlow.collectAsState(initial = null)
                val isFarmer by app.sessionManager.isFarmerFlow.collectAsState(initial = false)
                val appMode by app.sessionManager.appModeFlow.collectAsState(initial = AppMode.BUYING)
                val userDistrictId by app.sessionManager.userDistrictIdFlow.collectAsState(initial = 1)

                // ViewModels
                val authViewModel = remember { AuthViewModel(app.repository) }
                val marketplaceViewModel = remember { MarketplaceViewModel(app.repository, null) }
                val orderViewModel = remember { OrderViewModel(app.repository) }
                val farmerViewModel = remember { FarmerViewModel(app.repository) }
                val chatViewModel = remember { ChatViewModel(app.repository) }
                val profileViewModel = remember { ProfileViewModel(app.repository, app.sessionManager) }

                // Selected Listing Cache for Navigation
                var selectedListing by remember { mutableStateOf<ListingItem?>(null) }
                var chatListing by remember { mutableStateOf<ListingItem?>(null) }
                var unreadNotificationsCount by remember { mutableIntStateOf(0) }

                // Determine start destination
                val startDestination = if (authToken.isNullOrEmpty()) "login" else "marketplace"

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: startDestination

                // Session Expiration handler: When expired, silently redirect to login without showing jwt expired text
                LaunchedEffect(Unit) {
                    app.sessionManager.sessionExpiredFlow.collect {
                        authViewModel.resetAuth()
                        if (currentRoute != "login") {
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                }

                LaunchedEffect(authToken) {
                    if (authToken.isNullOrEmpty() && currentRoute != "login" && currentRoute != "onboarding") {
                        authViewModel.resetAuth()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }

                val conversations by chatViewModel.conversations.collectAsState()
                val unreadMessagesCount = remember(conversations) {
                    conversations.sumOf { it.unreadCount }
                }

                val showBars = currentRoute in listOf(
                    "marketplace", "my_orders", "notifications", "profile",
                    "my_listings", "farmer_orders", "dashboard", "messages"
                )

                Scaffold(
                    contentWindowInsets = if (showBars) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
                    topBar = {
                        if (showBars) {
                            AgroTopBar(
                                currentMode = appMode,
                                isFarmer = isFarmer,
                                unreadNotifications = unreadNotificationsCount,
                                onModeToggle = { newMode ->
                                    scope.launch {
                                        app.sessionManager.setAppMode(newMode)
                                        if (newMode == AppMode.BUYING) {
                                            navController.navigate("marketplace") {
                                                popUpTo("marketplace") { inclusive = true }
                                            }
                                        } else {
                                            navController.navigate("my_listings") {
                                                popUpTo("my_listings") { inclusive = true }
                                            }
                                        }
                                    }
                                },
                                onNotificationsClick = { navController.navigate("notifications") },
                                onBecomeFarmerClick = {
                                    authViewModel.initFarmerOnboarding()
                                    navController.navigate("onboarding")
                                }
                            )
                        }
                    },
                    bottomBar = {
                        if (showBars) {
                            AgroBottomNavigation(
                                currentRoute = currentRoute,
                                currentMode = appMode,
                                unreadNotifications = unreadNotificationsCount,
                                unreadMessages = unreadMessagesCount,
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (showBars) Modifier.padding(innerPadding) else Modifier)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination
                        ) {
                            // 1. Auth & Onboarding
                            composable("login") {
                                LoginScreen(
                                    viewModel = authViewModel,
                                    onSessionReady = {
                                        navController.navigate("marketplace") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    },
                                    onNeedsOnboarding = {
                                        navController.navigate("onboarding") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable("onboarding") {
                                OnboardingScreen(
                                    viewModel = authViewModel,
                                    onComplete = {
                                        navController.navigate("marketplace") {
                                            popUpTo("onboarding") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // 2. Marketplace
                            composable("marketplace") {
                                MarketplaceScreen(
                                    viewModel = marketplaceViewModel,
                                    onListingClick = { listing ->
                                        selectedListing = listing
                                        navController.navigate("listing_detail")
                                    },
                                    onChatClick = { listing ->
                                        chatListing = listing
                                        navController.navigate("chat_listing/${listing.id}")
                                    }
                                )
                            }

                            composable("listing_detail") {
                                val listing = selectedListing
                                if (listing != null) {
                                    ListingDetailScreen(
                                        listing = listing,
                                        reviews = emptyList(),
                                        onBackClick = { navController.popBackStack() },
                                        onOrderClick = {
                                            orderViewModel.initPlaceOrder(listing, userDistrictId, 1)
                                            navController.navigate("place_order")
                                        },
                                        onChatClick = {
                                            chatListing = listing
                                            navController.navigate("chat_listing/${listing.id}")
                                        },
                                        canDelete = isFarmer || appMode == AppMode.SELLING,
                                        onDeleteClick = {
                                            farmerViewModel.deleteListing(listing.id, userId ?: "farmer_me") {
                                                marketplaceViewModel.refreshMarketplace()
                                                navController.popBackStack()
                                            }
                                        }
                                    )
                                }
                            }

                            composable("place_order") {
                                PlaceOrderScreen(
                                    viewModel = orderViewModel,
                                    districts = marketplaceViewModel.uiState.collectAsState().value.districts,
                                    onBackClick = { navController.popBackStack() },
                                    onOrderPlaced = { orderId ->
                                        navController.navigate("order_detail/$orderId") {
                                            popUpTo("marketplace")
                                        }
                                    }
                                )
                            }

                            // 3. Orders
                            composable("my_orders") {
                                val orders by orderViewModel.buyerOrders.collectAsState()
                                val loading by orderViewModel.isLoadingOrders.collectAsState()

                                LaunchedEffect(Unit) {
                                    val currentId = userId ?: ""
                                    orderViewModel.loadBuyerOrders(currentId)
                                }

                                OrdersListScreen(
                                    orders = orders,
                                    isFarmer = false,
                                    isLoading = loading,
                                    onRefresh = {
                                        scope.launch {
                                            val currentId = userId ?: ""
                                            orderViewModel.loadBuyerOrders(currentId)
                                        }
                                    },
                                    onOrderClick = { order ->
                                        navController.navigate("order_detail/${order.id}")
                                    }
                                )
                            }

                            composable("farmer_orders") {
                                val orders by orderViewModel.farmerOrders.collectAsState()
                                val loading by orderViewModel.isLoadingOrders.collectAsState()

                                LaunchedEffect(Unit) {
                                    val currentId = userId ?: ""
                                    orderViewModel.loadFarmerOrders(currentId)
                                }

                                OrdersListScreen(
                                    orders = orders,
                                    isFarmer = true,
                                    isLoading = loading,
                                    onRefresh = {
                                        scope.launch {
                                            val currentId = userId ?: ""
                                            orderViewModel.loadFarmerOrders(currentId)
                                        }
                                    },
                                    onOrderClick = { order ->
                                        navController.navigate("order_detail/${order.id}")
                                    }
                                )
                            }

                            composable(
                                route = "order_detail/{orderId}",
                                arguments = listOf(navArgument("orderId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                                OrderDetailScreen(
                                    orderId = orderId,
                                    currentUserId = userId ?: "",
                                    viewModel = orderViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onChatClick = { oId, status ->
                                        navController.navigate("chat/$oId/$status")
                                    },
                                    onLaunchPayHere = { payReq ->
                                        launchPayHerePayment(payReq)
                                    }
                                )
                            }

                            // 4. Chat & Messages
                            composable("messages") {
                                val isSelling = (appMode == AppMode.SELLING)
                                val currentUserId = userId ?: ""
                                MessagesScreen(
                                    userId = currentUserId,
                                    isFarmer = isSelling,
                                    chatViewModel = chatViewModel,
                                    onConversationClick = { conv ->
                                        val foundListing = marketplaceViewModel.uiState.value.allListings.find { it.id == conv.listingId }
                                        val targetListing = foundListing ?: ListingItem(
                                            id = conv.listingId,
                                            farmerId = if (isSelling) currentUserId else conv.counterpartId,
                                            cropName = conv.cropName,
                                            pricePerKg = conv.pricePerKg,
                                            quantityAvailable = conv.quantityKg,
                                            minOrderKg = 1.0,
                                            harvestDate = "2026-10-01",
                                            photos = listOfNotNull(conv.listingPhoto),
                                            farmerFirstName = if (isSelling) "Me" else conv.counterpartName
                                        )
                                        chatListing = targetListing
                                        selectedListing = targetListing
                                        navController.navigate("chat/${conv.orderId}/${conv.status}")
                                    },
                                    onBackClick = {
                                        navController.navigate(if (isSelling) "my_listings" else "marketplace")
                                    }
                                )
                            }

                            composable(
                                route = "chat/{orderId}/{status}",
                                arguments = listOf(
                                    navArgument("orderId") { type = NavType.StringType },
                                    navArgument("status") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                                val status = backStackEntry.arguments?.getString("status") ?: ""
                                val isSelling = (appMode == AppMode.SELLING)
                                val currentUserId = userId ?: ""
                                ChatScreen(
                                    orderId = orderId,
                                    orderStatus = status,
                                    currentUserId = currentUserId,
                                    isFarmerView = isSelling,
                                    listing = chatListing ?: selectedListing,
                                    viewModel = chatViewModel,
                                    onRequestOrderFromChat = { l ->
                                        selectedListing = l
                                        orderViewModel.initPlaceOrder(l, userDistrictId, 1)
                                        navController.navigate("place_order")
                                    },
                                    onBackClick = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = "chat_listing/{listingId}",
                                arguments = listOf(navArgument("listingId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val listingId = backStackEntry.arguments?.getString("listingId") ?: ""
                                val listing = chatListing ?: marketplaceViewModel.uiState.value.allListings.find { it.id == listingId }
                                val isSelling = (appMode == AppMode.SELLING)
                                val currentUserId = userId ?: ""
                                ChatScreen(
                                    orderId = "inq_$listingId",
                                    orderStatus = "inquiry",
                                    currentUserId = currentUserId,
                                    isFarmerView = isSelling,
                                    listing = listing,
                                    viewModel = chatViewModel,
                                    onRequestOrderFromChat = { l ->
                                        selectedListing = l
                                        orderViewModel.initPlaceOrder(l, userDistrictId, 1)
                                        navController.navigate("place_order")
                                    },
                                    onBackClick = { navController.popBackStack() }
                                )
                            }

                            // 5. Selling Mode Screens
                            composable("dashboard") {
                                FarmerDashboardScreen(
                                    farmerId = userId ?: "farmer_me",
                                    viewModel = farmerViewModel,
                                    onAddListingClick = {
                                        selectedListing = null
                                        navController.navigate("add_edit_listing")
                                    }
                                )
                            }

                            composable("my_listings") {
                                MyListingsScreen(
                                    farmerId = userId ?: "farmer_me",
                                    viewModel = farmerViewModel,
                                    onAddListingClick = {
                                        selectedListing = null
                                        navController.navigate("add_edit_listing")
                                    },
                                    onEditListingClick = { listing ->
                                        selectedListing = listing
                                        navController.navigate("add_edit_listing")
                                    },
                                    onViewInquiriesClick = { listing ->
                                        chatListing = listing
                                        selectedListing = listing
                                        navController.navigate("chat_listing/${listing.id}")
                                    }
                                )
                            }

                            composable("add_edit_listing") {
                                AddEditListingScreen(
                                    farmerId = userId ?: "farmer_me",
                                    existingListing = selectedListing,
                                    viewModel = farmerViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onSaved = {
                                        marketplaceViewModel.refreshMarketplace()
                                        navController.popBackStack()
                                    }
                                )
                            }

                            // 6. Notifications & Profile
                            composable("notifications") {
                                NotificationsScreen(
                                    userId = userId ?: "buyer_me",
                                    repository = app.repository,
                                    onOrderClick = { orderId ->
                                        navController.navigate("order_detail/$orderId")
                                    }
                                )
                            }

                            composable("profile") {
                                ProfileScreen(
                                    userId = userId ?: "buyer_me",
                                    viewModel = profileViewModel,
                                    onBecomeFarmerClick = {
                                        profileViewModel.openFarmerRegistrationDialog()
                                    },
                                    onLoggedOut = {
                                        authViewModel.resetAuth()
                                        scope.launch {
                                            app.sessionManager.clearSession()
                                            navController.navigate("login") {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchPayHerePayment(req: PayHerePaymentRequest) {
        // Launches payment checkout flow.
        // PayHere SDK (lk.payhere:androidsdk) handles server-to-server webhook confirmation
        Toast.makeText(this, "Opening PayHere checkout for ${req.amount} LKR...", Toast.LENGTH_LONG).show()
    }
}
