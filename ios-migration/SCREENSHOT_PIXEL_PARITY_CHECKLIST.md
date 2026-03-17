# iOS vs Android Screenshot Pixel Parity Checklist

Date: 2026-03-16  
Branch: `ios-migration-starter-v3`

Use this checklist to compare Android and iOS screen pairs side-by-side and close final visual gaps.

## 1) Capture Protocol

For each screen pair:
- Capture Android screenshot from the same state/data.
- Capture iOS screenshot from the same state/data.
- Use equivalent viewport class:
  - Android: common phone viewport.
  - iOS: iPhone simulator/device viewport.
- Compare in three states where relevant:
  - default state
  - loading/empty state
  - error or validation state

Acceptance tolerance:
- Spacing gap: within 2-4 px visual difference.
- Font hierarchy: same relative order and emphasis.
- Icon scale: visually equivalent prominence.
- CTA priority: same primary action dominance.

## 2) Global Visual Tokens Checklist

Check these on every screen:
- [ ] Top app bar height and title alignment are visually matched.
- [ ] Section headers use the same hierarchy (size + weight + spacing above/below).
- [ ] Card corner radius and elevation depth feel equivalent.
- [ ] Primary button height/weight/color prominence matches Android.
- [ ] Input field vertical rhythm matches Android forms.
- [ ] Bottom CTA/FAB placement and reachability match Android behavior.
- [ ] Empty states have similar icon size, text order, and spacing.
- [ ] Loading states use centered indicator and equivalent surrounding whitespace.
- [ ] List row padding and separator treatment are consistent.

## 3) Screen-by-Screen Checklist

Status legend:
- `PASS` = no visible parity issue.
- `TUNE` = minor spacing/type/icon adjustment needed.
- `BLOCK` = behavior/layout mismatch.

### A) Auth

| ID | Android Reference | iOS Target | Visual Checks | Status |
|---|---|---|---|---|
| AUTH-01 | `Login` | `Features/Auth/LoginView.swift` | Staff login visibility, role selector, resend verification placement, primary CTA prominence |  |
| AUTH-02 | `SignUp` | `Features/Auth/SignUpView.swift` | Account type selector hierarchy, role-specific fields spacing, validation text spacing |  |
| AUTH-03 | `Forgot Password` | `Features/Auth/ForgotPasswordView.swift` | Field spacing, helper text location, button hierarchy |  |
| AUTH-04 | `Email Verification` | `Features/Auth/EmailVerificationView.swift` | Code input spacing, timer/resend positioning, confirm CTA weight |  |

### B) Volunteer Core

| ID | Android Reference | iOS Target | Visual Checks | Status |
|---|---|---|---|---|
| VOL-01 | Home | `Features/Volunteer/VolunteerHomeTabView.swift` | Tab spacing, card rhythm, top actions visual priority |  |
| VOL-02 | Events list | `Features/Volunteer/Events/EventsListView.swift` | Row card density, filter controls, empty/loading states |  |
| VOL-03 | Event detail | `Features/Volunteer/Events/EventDetailView.swift` | Header media/title stack, requirement blocks, apply CTA prominence |  |
| VOL-04 | Jobs list | `Features/Volunteer/Jobs/JobsListView.swift` | Job card hierarchy, compensation line emphasis, action placement |  |
| VOL-05 | Job detail | `Features/Volunteer/Jobs/JobDetailView.swift` | Section spacing (requirements/responsibilities), apply status UI |  |
| VOL-06 | My Activity | `Features/Volunteer/Activity/MyActivityView.swift` | Status chips color/weight, grouped sections, row spacing |  |

### C) Community Hub

| ID | Android Reference | iOS Target | Visual Checks | Status |
|---|---|---|---|---|
| COM-01 | Community hub | `Features/Community/CommunityHubView.swift` | Tile/list density, grouping labels, icon scale |  |
| COM-02 | MindLoom feed | `Features/Community/MindLoom/MindLoomFeedView.swift` | Dark theme parity, creator strip size, For You/Following tabs, floating Create CTA |  |
| COM-03 | MindLoom composer | `Features/Community/MindLoom/MindLoomFeedView.swift` (sheet) | Field sizing, media attach controls, Post button dominance |  |
| COM-04 | MindLoom profile | `Features/Community/MindLoom/MindLoomProfileView.swift` | Avatar/name/stats stack, follow/message/share actions, post grid/list rhythm |  |
| COM-05 | Sponsored list | `Features/Community/Sponsored/SponsoredContentView.swift` | Sponsored/Garage segment tabs, card media height, action button row |  |
| COM-06 | Create Ad | `Features/Community/Sponsored/CreateAdvertisementView.swift` | Section headers, media carousel cards, form spacing, publish CTA |  |
| COM-07 | Create Garage Sale | `Features/Community/Sponsored/CreateGarageSaleView.swift` | Section grouping, address/contact blocks, media cards, publish CTA |  |
| COM-08 | Marketplace | `Features/Community/Marketplace/MarketplaceView.swift` | List row density, price emphasis, filter controls layout |  |
| COM-09 | Gallery uploads | `Features/Community/Gallery/GalleryUploadsView.swift` | Upload picker placement, preview card size, list row visuals |  |

### D) Wallet and Payments

| ID | Android Reference | iOS Target | Visual Checks | Status |
|---|---|---|---|---|
| WAL-01 | Wallet home | `Features/Wallet/GlobalWalletHomeView.swift` | Balance hero prominence, quick actions order |  |
| WAL-02 | Transact | `Features/Wallet/WalletTransactView.swift` | Transfer mode selector, amount input, summary card hierarchy |  |
| WAL-03 | History | `Features/Wallet/WalletTransactionHistoryView.swift` | Row status color chips, amount alignment, date text scale |  |
| WAL-04 | Payment methods | `Features/Shared/Payments/PaymentMethodsView.swift` | Card rows, add method CTA, status indicators |  |

### E) Organizer and Employer

| ID | Android Reference | iOS Target | Visual Checks | Status |
|---|---|---|---|---|
| ORG-01 | Organizer tabs | `Features/Organizer/OrganizerHomeTabView.swift` | Tab labels, icon prominence, safe-area fit |  |
| ORG-02 | Hosted events | `Features/Organizer/Hosted/OrganizerHostedEventsView.swift` | List card spacing, event status visuals |  |
| ORG-03 | Event composer | `Features/Organizer/Hosted/OrganizerEventComposerView.swift` | Form grouping, date/time field emphasis, save CTA |  |
| ORG-04 | Applications review | `Features/Organizer/Applications/OrganizerApplicationsReviewView.swift` | All/Pending/Approved/Rejected tabs and status styling |  |
| ORG-05 | Organizer wallet | `Features/Organizer/Wallet/OrganizerWalletView.swift` | Withdraw section hierarchy, action readability |  |
| EMP-01 | Employer tabs | `Features/Employer/EmployerHomeTabView.swift` | Tab balance and labels |  |
| EMP-02 | Posted jobs | `Features/Employer/PostedJobs/EmployerPostedJobsView.swift` | List card spacing and CTA placement |  |
| EMP-03 | Job composer | `Features/Employer/PostedJobs/EmployerJobComposerView.swift` | Form density, date/time inputs, publish CTA |  |
| EMP-04 | Applications review | `Features/Employer/Applications/EmployerApplicationsReviewView.swift` | Filter tabs parity, approve/reject buttons weight |  |

### F) Shared Tools and Profile

| ID | Android Reference | iOS Target | Visual Checks | Status |
|---|---|---|---|---|
| SHR-01 | Chat list | `Features/Shared/Chat/ConversationsListView.swift` | Row avatar/title/subtitle spacing and unread emphasis |  |
| SHR-02 | Chat detail | `Features/Shared/Chat/ConversationDetailView.swift` | Bubble spacing, input dock placement, send icon sizing |  |
| SHR-03 | Calls | `Features/Shared/Calls/CallHistoryView.swift` | Dialed/missed/received visual distinction |  |
| SHR-04 | Live sessions | `Features/Shared/Live/LiveSessionsView.swift` | Session cards and join CTA visual priority |  |
| SHR-05 | Date hub | `Features/Shared/Date/DateHubView.swift` | Segmented controls, invitation cards, action CTA hierarchy |  |
| PROF-01 | Profile home | `Features/Profile/ProfileHomeView.swift` | Avatar/name/action block spacing |  |
| PROF-02 | Account security | `Features/Profile/AccountSecurityView.swift` | Setting rows, helper text spacing, action emphasis |  |
| SUP-01 | AI assistant | `Features/Shared/Support/AIAssistantView.swift` | Search input + FAQ/chat rhythm |  |
| SUP-02 | Privacy policy | `Features/Shared/Support/PrivacyPolicyView.swift` | Header/body typography spacing |  |
| SUP-03 | Terms | `Features/Shared/Support/TermsAndConditionsView.swift` | Header/body typography spacing |  |
| SUP-04 | Support center | `Features/Shared/Support/SupportCenterView.swift` | Contact tiles and action controls |  |

### G) Admin/Owner

| ID | Android Reference | iOS Target | Visual Checks | Status |
|---|---|---|---|---|
| ADM-01 | Admin home | `Features/Admin/AdminHomeTabView.swift` | Entry tile hierarchy and nav clarity |  |
| ADM-02 | Dashboard | `Features/Admin/Dashboard/OwnerDashboardView.swift` | KPI cards spacing and top-level action priorities |  |
| ADM-03 | Payout queue | `Features/Admin/Payouts/AdminPayoutQueueView.swift` | Table/list density and status visuals |  |
| ADM-04 | Support console | `Features/Admin/SupportConsole/SupportConsoleView.swift` | Search/filter bar and result row spacing |  |
| ADM-05 | User reports | `Features/Admin/Reports/OwnerUserReportsView.swift` | Report card readability and metadata hierarchy |  |
| ADM-06 | KYC review | `Features/Admin/KYC/OwnerKYCReviewView.swift` | Approve/reject action prominence and row layout |  |
| ADM-07 | Fee settings | `Features/Admin/Config/OwnerFeeSettingsView.swift` | Numeric input spacing and helper copy alignment |  |
| ADM-08 | System config | `Features/Admin/Config/OwnerSystemConfigView.swift` | Toggle row spacing and section grouping |  |

## 4) Sign-off Sheet

Run complete when:
- [ ] All screens above marked `PASS` or have documented follow-up patch.
- [ ] No `BLOCK` remains in core user flows.
- [ ] Before/after screenshot pairs stored for each `TUNE` fix.

Recommended sign-off format:
- Reviewer:
- Date:
- Device pair:
- Outstanding items (if any):
