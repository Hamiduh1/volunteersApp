/* eslint-disable max-len */
/**
 * Member-facing guide the AI assistant answers from. Keep it in sync with the apps:
 * exact screen and button names, steps, limits and fees. No admin-only tools or secrets.
 */
export const APP_ASSISTANT_GUIDE = `Screen and button names below come from the iPhone app; the Android app uses the same names in nearly all places. On Android, "Face ID / Touch ID / iPhone passcode" means fingerprint, face unlock or the phone's screen lock, and "iPhone Settings" means the phone's Settings app. Fees and limits are current defaults and can change; the amount shown in the app before paying is always the one that applies.

## Accounts, Sign-In and Roles
- Roles: Volunteer, Organizer, Employer (anyone can sign up as these). Staff accounts are created by the company.
- Create Account > pick Account type (Volunteer / Organizer / Employer) > Full name, Email address, Country, Phone number, Password, Confirm password > "Create Account". Organizers also give Organization name; Employers give Company name; Volunteers give Date of birth and must be 18+. Password: at least 6 characters and must match.
- Phone numbers must be international with the country code (e.g. +256…, +1…, or 0044…).
- Verify email: a 6-digit code is emailed. On the Verify screen enter it and tap "Verify Code". "Resend verification email" works once every 120 seconds. If email doesn't arrive, use "Verify by phone OTP" > Send phone OTP > enter the 6-digit SMS code > Verify phone OTP.
- Sign In: Email address + Password > "Sign In". "Need help signing in?" offers resend verification or phone verification.
- Forgot Password: enter email > "Send Reset Link" (an emailed link, not a code). You can request again every 45 seconds.
- One phone at a time: signing in on a new phone shows "Signed in on another phone" with "Log out other phone" or "Keep other phone signed in".
- Volunteer tabs: Home, Events, Jobs, Live, Loop (Community Hub). Menu (top left): My Profile, My Activity, Transfers, Payment Methods, Account Settings, Accounting Settings, Security & Privacy, Notifications, Community Alerts, Support section, Logout.
- Organizer tabs: Home (Organizer Hub), Events, Requests, Settlement, Summary. Menu adds Hosted Events, Applications, Settlement Mirror, Global Wallet, Organizer Profile, Payment Methods, Live Studio, and Community links (Community Hub, Social Inbox, User Directory, Marketplace, MindLoom, Sponsored, Date Hub).
- Employer tabs: Home (Employer Hub), Jobs, Requests, Transfers, Profile. Menu adds Posted Jobs, Employer Profile, Payment Methods, Live Sessions and the same Community links.
- All roles: the AI Assistant sparkle icon is at the top right.
- "Role is unknown. Please contact support." means the account needs support help. "Access Restricted" means that section isn't available to your account.

## Profile and Account
- Volunteer: Menu > My Profile. "Change Profile Photo" (up to about 2.5 MB after compression), Name (required), Username, Phone Number (international), Country > "Save Profile". Country is used to enable wallet and payout features.
- Organizer Profile: Name, Email, Organization Name, Location, Bio, Phone Number > "Save Organizer Profile".
- Employer Profile: "Change Organization Image", Name, Email, Organization Name, Contact Email, Description > "Save Employer Profile".
- Change password: Profile > Security Center > "Send Password Reset Email".
- Delete account: Security Center or Support Center > "Email Support For Account Deletion" (emails support@softsolutionstech.com with subject "Account Deletion Request"; include your account email).
- Unblock someone: Profile > Blocked Users > tap the member > "Unblock {name}".

## Volunteer: Events, Jobs and My Activity
- Events tab: search by title or location; filter All / Applied.
- Free event: open it > "Apply to event". You see "You're registered. The organizer will confirm your spot."
- Paid event: open it > "Choose payment" > "Pay by saved card". You pay in secure Stripe Checkout; the spot is confirmed automatically when payment completes. Mobile money can't be used for event tickets.
- Button states: Continue payment, Application received, You're going / Ticket confirmed, Application closed, Event full, Event closed.
- Cancel: "Cancel registration" (or "Keep my spot"). Only free registrations or unfinished payments, and only before the event starts. Paid tickets can't be cancelled in the app; message the organizer about a refund. After withdrawing you can register again; if the organizer rejects you, that's final.
- When an organizer streams an event, approved guests see a "Live now" card with "Watch live".
- Jobs tab: filter All / Open / Applied; search by title, role, organization or location. View Details > "Apply Now". After applying, "Upload resume (PDF)" or "Replace resume (PDF)": PDF only, max 6 MB. Statuses: Application Sent, Application Approved, Not Selected, Job Closed.
- My Activity (Menu): groups Pending, Approved, Rejected (declined/withdrawn), Other Updates; filter All / Events / Jobs; "Search activity".

## Organizer: Events and Applicants
- Create: Events tab (Hosted Events) > + (or Home > Create New Event). Sections: Basics (Title, Description, Category, Date & Time), Venue (Location Name, Address), Team Notes (Volunteer Requirements, Contact Info), Capacity & Settlement (Volunteer Limit, default 20; Per Volunteer Rate in USD, 0 = free) > "Publish Event". Title, description and location are required; limit must be above 0.
- Paid events need a payout route first: Payment Methods > Stripe business payouts (choose "Paid events"), or a verified bank or mobile money receive route.
- Ticket fee: by default the platform keeps 5% of each ticket; you receive the rest through Stripe.
- Each event row shows "Registered: X of Y" with Applicants, Edit, Go Live. Swipe a row for "Close entries" / "Reopen" or "Delete". Deleting an event with no registrations removes it; otherwise it is closed and hidden. End any live broadcast for the event before deleting it. Once people have registered you can't change the price or set the limit below the number registered.
- Requests tab (Applications): filter All / Pending / Approved / Rejected or by event; Approve or Reject.
- Summary tab: Applied / Pending / Approved / Rejected counts, limit and earnings per event.
- Settlement tab (Settlement Mirror): read-only record; filters All / Settlements / Archived. Paid-event money is paid out to you and isn't held in the app.
- You can't apply to your own event.

## Employer: Jobs and Applicants
- Post: Jobs tab (Posted Opportunities) > + (or Home > Post New Opportunity). Role Basics (Organization Name, Opportunity Title, Specific Role/Job Title), Schedule (Date & Time), Location & Team Size (Location, Category, Volunteers Needed at least 1), Role Details (description). Categories: Technology, Health, Education, Environment, Community, Animals, Arts & Culture, Seniors, Other.
- Manage: filter All / Open / Closed. Each row: Review Requests, Details, Edit, Close/Reopen, Archive. Archiving a job with applicants closes it and keeps it for records; jobs with no applicants are deleted.
- Review Requests > Approve or Decline. "Open application" shows the volunteer, resume and Approve/Reject.

## Transfers / Send Money
- Volunteers: Menu > Transfers (or Home > "Open Transfer Center"). Organizers: Menu > Global Wallet. Employers: Transfers tab.
- The app does not hold your money and has no stored balance. There is no Add Money or Withdraw. Cash pickup is coming soon.
- First-time security: verify the device (Face ID / Touch ID / phone passcode), then create a 6-digit Transfer Passcode. 5 wrong tries locks it for 5 minutes. An unlock lasts 10 minutes and ends when you leave the app. Forgot it: "Reset Transfer Passcode" (verifies the device again). The phone must have a screen lock/passcode turned on.
- Send money > choose App User, Mobile Money, or Bank Account. App User steps: 1 Recipient, 2 Delivery (the recipient's verified bank or mobile money), 3 Amount, 4 Funding. Mobile Money and Bank Account: 1 Recipient, 2 Amount, 3 Funding.
- Check the quote (fee, total, rate, what the recipient gets) and tap "Send now". If the quote expires, tap "Refresh quote".
- Default limits: minimum 1.00; per sender up to 15 transfers or $3,000 per 24 hours and $10,000 per 30 days; $2,000 per payout; only one App User transfer in progress at a time.
- Receipts: Refresh status, Download receipt PDF, Print receipt, Send receipt copy. History is in Activity (Receipts & Activity).
- If a send fails, money taken from the funding method is released or refunded.
- Recipients: Saved payout recipients and App members; Edit, Send, Remove.
- Mobile money delivery countries: Benin, Botswana, Cameroon, Cote d'Ivoire, Ethiopia, Gambia, Ghana, Guinea, Kenya, Madagascar, Malawi, Mozambique, Republic of the Congo, Rwanda, Senegal, Sierra Leone, Tanzania, Uganda, Zambia.

## Payment Methods
- Menu > Payment Methods. The + button: card, US bank (ACH), mobile money, or bank/SWIFT receiving.
- Funding (to pay for transfers): "Add card" (cards never receive money); "Link US bank" (verified through Stripe's secure bank login, USD debits only); "Add mobile money" (confirmed with a 6-digit SMS code). Mobile money can't fund bank or SWIFT deliveries. Mobile money funding countries: Benin, Cameroon, Cote d'Ivoire, Ethiopia, Kenya, Tanzania, Uganda.
- App User receive routes (to receive transfers from members): "Add bank or SWIFT" (local banks in Egypt, Ghana, Kenya, Nigeria, South Africa and the United States; SWIFT pays out in USD to many countries) and "Add mobile money". A bank saved for receiving cannot fund Send Money; to pay by bank use "Link US bank".
- Stripe business payouts: only needed to receive your own earnings (not for sending money, buying, or paying for ads). Choose the earnings type: Marketplace, Garage Sales, Dating services, Sponsored ads, Paid events, Organizer earnings, Other in-app commerce. Run setup again to add another type. After setup, return to the app and tap Refresh; "Seller payouts active" means it works.
- Each method: Set Default, Verify Now, Sync Route, Remove. "Action required" on a card means re-link it.

## Marketplace
- Home > Marketplace or Community Hub > Marketplace. "Search Marketplace", sort (distance needs location permission), heart = Saved, My listings.
- Sell: "Sell" > up to 10 photos/videos (first is the cover; long-press > "Make cover"), Title, Category, Condition (New / Like New / Good / Fair), Price (more than $0, up to $50,000), Description ("Write it for me" can draft it).
- Listings stay up 60 days, then are removed automatically. Up to 3 active listings; open a Garage Shop in Sponsored for unlimited listings. Shipping is coming later.
- Buy: "Buy now" > "Continue to payment" (secure Stripe payment). If someone else buys it first you're refunded automatically. Arrange pickup in Chat; meet in a busy public place and check the item. You can't buy your own item.
- Sellers need Stripe business payouts set up for Marketplace before buyers can pay. Platform fee: 2% by default.

## Sponsored (Ads and Garage Sales)
- Community Hub > Sponsored. Tabs: Overview, Ads, Garage.
- Post an ad: + > "Post an ad" (4 steps): 1 Type (Lost, Found, Notice, Document, Ad); 2 title (up to 120 characters), description (up to 2,000), place, up to 4 photos; 3 Contact (Chat, Call and/or WhatsApp); 4 Duration (7 or 30 days).
- Lost, Found, Notice and Document posts are free, up to 5 live at once. The "Ad" type may have a listing fee paid by card ("Continue to payment"); default $0. Found documents can't include photos and use in-app chat only.
- "Mark as resolved" / "Mark as found" takes the ad down 24 hours later. "My ads" has Edit and Delete.
- Post a sale: + > "Post a sale" (4 steps, free): 1 kind of sale and dates (up to 7 days); 2 title, description, up to 8 categories, up to 8 photos/videos; 3 address and contact; 4 how buyers can pay (Cash, Card in app, Mobile money, Bank transfer; "Card in app" needs a payout account) > "Publish sale".
- Sales are removed automatically the day after they end; "End sale now" or Delete anytime.
- Browse filters: All, Today, This weekend, Upcoming, Saved, Near me.
- Buyers: Chat, Call or Pay. Pay > "Pay seller" > enter the agreed amount ($0.01 to $50,000) > "Continue to secure checkout". Platform share: 2% by default. "How fees work" explains current fees.

## Community Hub, MindLoom and Community Alerts
- Community Hub (Loop tab): Social Inbox, User Directory, MindLoom, Dating Loop, Marketplace, Sponsored.
- MindLoom feeds: For You and Following. "Share a thought, clip, or photo..." > Create Post > attach one Photo, Video or Document (PDF) > "Post". Double-tap to like; open Comments to reply.
- Search Creators (at least 2 characters); Follow/Unfollow on their profile; your profile is "My MindLoom". Post menu: Edit Post, Delete Post, Report Post, Block.
- Upload limits: images under 12 MB, videos under 40 MB, PDFs under 16 MB.
- Community Alerts (Menu): invitation updates, stream notices, announcements; tap "Watch Live" alerts to join.
- Gallery Uploads isn't available in this version.

## Social Inbox, Chat, Calls and Blocking
- Social Inbox sections: Chats, Invitations, Calls.
- Start a chat: User Directory > find the member > "Send chat invite". They Accept or Decline under Invitations.
- Group: Social Inbox > "New Group" > Group name, optional first message, Select Members. "Leave Group" in the group menu. A group needs another member before calls work.
- In chat: Photo (up to 8 MB), Video (under 20 MB), Voice message (under 8 MB). Long-press a message for Reply, Copy, Share.
- Calls: phone (voice) or video icon at the top of a chat; you can switch voice/video during a call. Lock-screen call alerts need a real phone with notifications allowed. Call history: Calls (Received, Dialed, Missed).
- Block: User Directory > ... > "Block user"; MindLoom, Live and Dating each have their own Block. Unblock in Profile > Blocked Users.
- Report reasons: Harassment or bullying, Hate speech or symbols, Nudity or sexual content, Violence or dangerous acts, Self-harm or suicide, Involves a minor, Spam or scam, False information, Something else.

## Live
- Live tab (volunteers), Live Studio (organizers), Live Sessions (employers). All roles can go live.
- "Go Live" > Stream title > Who can watch? (Everyone / Followers / Invite Only / Accepted Event Guests) > Who can go on stage? (Host Only / Request To Join / Host Approved Guests / Accepted Event Guests) > Replay access (Owner Only / Shared Link / Followers / Public) > "Notify followers when live starts".
- Organizers can pick an event under "Stream for an event" so accepted guests are notified.
- The app checks camera, microphone and internet first; if blocked, tap "Open Settings".
- "End Stream" finishes; the replay then processes.
- Live Tools: approve stage requests, "Enable Live Chat" on/off, block/unblock viewers, share, post replay to MindLoom.
- Viewers: Watch live, Watch replay, "Report this live stream", Block host.
- Hosts: "Delete Broadcast" removes the stream, its replay, chat and MindLoom post.

## Dating
- Home > Dating Loop or Community Hub > Dating Loop (organizers/employers: Date Hub). Adults only: you confirm 18+ and your account birth date is checked.
- Dating Profile: up to 6 photos, Name, Bio, Gender, Looking For, Relationship goal, up to 5 interests, up to 3 prompts (150 characters each), optional job title and school. Only your age is shown, not your birthday. "Preview your card"; "Delete Dating Profile".
- Dating Loop is free. Views: Swipe / People / Photos. Like and comment; mutual likes are a match.
- Blind Date: private one-on-one chats. Needs a pass (1 pass = 1 chat), paid by Card or Mobile money via "Continue to secure checkout"; the price is shown in the app. A chat opens when both people have a pass. Sections: Your matches, Invites for you, Discover.
- Report reasons: Fake profile or scam, Inappropriate photos, Harassment or hate, May be under 18, Spam or selling, Something else.
- Blocked members can't appear in Dating Loop or Blind Date or send you likes or chat requests.

## Support Center, AI Assistant and Contact
- Support Center (Menu): Call Support, Email Support, Open Support Link, Open Live Chat (opens the AI Assistant), Email Support For Account Deletion.
- Phone (614) 380-8065; email support@softsolutionstech.com; website https://softsolutionstech.com; privacy policy https://volunteersapp-968b2.web.app/privacy.html.
- AI Assistant: sparkle icon at the top right or Menu > AI Assistant. The question list button next to the message box has suggested and common questions. Options menu: "New conversation", "Email support". "Didn't help? Send to support" forwards the question to the support team, who reply by email.
- Volunteers also have Feedback (1–5 stars > "Submit Feedback") and Report Issue (title, category, details > "Submit Report"). Categories: General, Payments, Events, Jobs, Chat, Community, Security.

## Notifications and Troubleshooting
- Menu > Notifications: Inbox and Settings. Toggles: New Followers, New MindLoom Posts, Live Streams, Dating Activity, Event Reminders, App Updates (all on by default except App Updates).
- Not getting notifications: "Open iPhone Settings" to allow them, or "Check push setup" to test Social Inbox notifications.
- Permissions: Camera (live/media), Microphone (voice/live), Photos (attachments), Location (nearby listings), Face ID (unlocking Transfers).
- Common messages: "Network unavailable. Check your connection and try again."; "Too many attempts. Please wait a moment and try again."; "Your session expired. Sign in again."; "This email is already registered. Sign in or reset the password."
- Verification email missing: use phone OTP on the Verify screen.
- Mobile money number won't verify: check country, network and number; no money was moved.
- Statement export says "Statement export is coming soon."; receipts are in each transaction's details.

## Other Features
- FX Calculator: live exchange-rate estimate in Transfers.
- Accounting Settings: receipts; statement export coming soon.
- AML/CFT Questionnaire: plain-language compliance overview.
- How to Use: short in-app guide. Privacy Policy and Terms screens.
- Seller and advertiser shop profiles in Marketplace and Sponsored.`;
