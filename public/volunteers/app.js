import {initializeApp} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js";
import {
  ReCaptchaEnterpriseProvider,
  getToken,
  initializeAppCheck,
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-app-check.js";
import {
  getAuth,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut,
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-auth.js";
import {getFunctions, httpsCallable} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-functions.js";

const firebaseConfig = {
  apiKey: "AIzaSyC-ahxv_2C26bveNGx2Dr2y3901tUql1p0",
  authDomain: "volunteersapp-968b2.firebaseapp.com",
  projectId: "volunteersapp-968b2",
  storageBucket: "volunteersapp-968b2.firebasestorage.app",
  messagingSenderId: "6677623239",
  appId: "1:6677623239:web:0db3ba55037c77ffd829e2",
};

const app = initializeApp(firebaseConfig);
const appCheck = initializeAppCheck(app, {
  provider: new ReCaptchaEnterpriseProvider("6LdUhHQtAAAAAKi_gVLY2d2LuNcQdtcxcBnRXWKc"),
  isTokenAutoRefreshEnabled: true,
});
const auth = getAuth(app);
const functions = getFunctions(app, "us-central1");
const getVolunteerListings = httpsCallable(functions, "getVolunteerListings");
const applyForEvent = httpsCallable(functions, "applyForEvent");
const applyForJob = httpsCallable(functions, "applyForJob");

const state = {
  user: null,
  activeTab: "events",
  events: [],
  jobs: [],
  applying: new Set(),
};

const refs = {
  sessionTitle: document.querySelector("#session-title"),
  sessionCopy: document.querySelector("#session-copy"),
  loginForm: document.querySelector("#login-form"),
  loginEmail: document.querySelector("#login-email"),
  loginPassword: document.querySelector("#login-password"),
  loginButton: document.querySelector("#login-button"),
  showLoginButton: document.querySelector("#show-login-button"),
  signOutButton: document.querySelector("#sign-out-button"),
  status: document.querySelector("#portal-status"),
  eventsTab: document.querySelector("#events-tab"),
  jobsTab: document.querySelector("#jobs-tab"),
  eventsPanel: document.querySelector("#events-panel"),
  jobsPanel: document.querySelector("#jobs-panel"),
  eventsList: document.querySelector("#events-list"),
  jobsList: document.querySelector("#jobs-list"),
  refreshButton: document.querySelector("#refresh-button"),
  registerDialog: document.querySelector("#register-dialog"),
  closeDialogButton: document.querySelector("#close-dialog-button"),
};

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function showStatus(message, tone = "info") {
  refs.status.textContent = message || "";
  refs.status.className = message ? `status ${tone}` : "status";
}

function messageFrom(error, fallback) {
  const message = String(error?.message || fallback || "Something went wrong.").trim();
  return message.replace(/^\[functions\/[^\]]+\]\s*/i, "");
}

async function requireAppCheckToken() {
  try {
    await getToken(appCheck);
  } catch (error) {
    throw new Error("Website security verification could not start. Refresh and try again.");
  }
}

function formatDate(milliseconds) {
  if (!Number.isFinite(Number(milliseconds)) || Number(milliseconds) <= 0) return "Date to be confirmed";
  return new Intl.DateTimeFormat(undefined, {dateStyle: "medium", timeStyle: "short"}).format(new Date(Number(milliseconds)));
}

function truncateText(value, limit = 190) {
  const clean = String(value || "").trim();
  if (!clean) return "Details will be shared by the organizer or employer.";
  return clean.length > limit ? `${clean.slice(0, limit).trim()}...` : clean;
}

function applicationBadge(status) {
  const clean = String(status || "").trim().toUpperCase();
  if (!clean) return "";
  const label = clean === "PENDING" ? "Application sent" : `Application: ${clean.toLowerCase().replaceAll("_", " ")}`;
  return `<span class="application-state">${escapeHtml(label)}</span>`;
}

function listingMarkup(listing) {
  const isEvent = listing.type === "EVENT";
  const isApplying = state.applying.has(`${listing.type}:${listing.id}`);
  const organization = isEvent ? "Organizer event" : listing.organizationName || "Organization";
  const secondaryTitle = isEvent ? "" : listing.roleTitle;
  const dateLabel = isEvent ? "Event" : "Application deadline";
  const date = isEvent ? listing.occursAtMs : listing.applicationDeadlineMs || listing.occursAtMs;
  const capacity = isEvent && listing.volunteerLimit > 0
    ? `${listing.participantsCount || 0} / ${listing.volunteerLimit} volunteers`
    : "Volunteer opportunity";
  const payment = isEvent && Number(listing.paymentUsd) > 0 ? `$${Number(listing.paymentUsd).toFixed(2)} per volunteer` : "No application fee";
  const actionLabel = isApplying ? "Sending application..." : isEvent ? "Apply to event" : "Apply to opportunity";

  return `
    <article class="listing-card">
      <div>
        <span class="eyebrow" style="color: #8f551b;">${escapeHtml(organization)}</span>
        <h3>${escapeHtml(listing.title)}</h3>
        ${secondaryTitle ? `<p style="margin: -2px 0 10px; color: #34596b; font-weight: 800;">${escapeHtml(secondaryTitle)}</p>` : ""}
        <div class="listing-meta">
          <span>${escapeHtml(listing.category || "Community")}</span>
          <span>${escapeHtml(listing.location || "Location to be confirmed")}</span>
          <span>${escapeHtml(dateLabel)}: ${escapeHtml(formatDate(date))}</span>
          <span class="accent">${escapeHtml(isEvent ? payment : capacity)}</span>
        </div>
        <p class="description">${escapeHtml(truncateText(listing.description))}</p>
      </div>
      <div class="listing-footer">
        <small>${escapeHtml(isEvent ? capacity : "Review and response remain with the employer")}</small>
        ${listing.applicationStatus ? applicationBadge(listing.applicationStatus) : `<button class="button primary listing-action" type="button" data-action="apply" data-type="${escapeHtml(listing.type)}" data-id="${escapeHtml(listing.id)}" ${isApplying ? "disabled" : ""}>${escapeHtml(actionLabel)}</button>`}
      </div>
    </article>
  `;
}

function renderListings() {
  const renderList = (element, listings, emptyMessage) => {
    element.innerHTML = listings.length
      ? listings.map(listingMarkup).join("")
      : `<div class="empty">${escapeHtml(emptyMessage)}</div>`;
  };
  renderList(refs.eventsList, state.events, "No open events are available right now. Check back soon.");
  renderList(refs.jobsList, state.jobs, "No open opportunities are available right now. Check back soon.");
}

function renderSession() {
  const signedIn = Boolean(state.user);
  refs.loginForm.classList.remove("is-open");
  refs.showLoginButton.classList.toggle("hidden", signedIn);
  refs.signOutButton.classList.toggle("hidden", !signedIn);
  if (!signedIn) {
    refs.sessionTitle.textContent = "Ready to apply?";
    refs.sessionCopy.textContent = "New volunteers register in the mobile app. Returning volunteers can sign in here to apply on the web.";
    return;
  }
  const email = state.user.email || "your VolunteersApp account";
  refs.sessionTitle.textContent = "You are ready to apply.";
  refs.sessionCopy.textContent = `Signed in as ${email}. Your applications will also appear in VolunteersApp.`;
}

function selectTab(tab) {
  state.activeTab = tab;
  const eventsActive = tab === "events";
  refs.eventsTab.setAttribute("aria-selected", String(eventsActive));
  refs.jobsTab.setAttribute("aria-selected", String(!eventsActive));
  refs.eventsPanel.classList.toggle("is-active", eventsActive);
  refs.jobsPanel.classList.toggle("is-active", !eventsActive);
  refs.eventsPanel.hidden = !eventsActive;
  refs.jobsPanel.hidden = eventsActive;
}

async function loadListings({quiet = false} = {}) {
  if (!quiet) showStatus("Loading current opportunities...", "info");
  refs.refreshButton.disabled = true;
  try {
    await requireAppCheckToken();
    const response = await getVolunteerListings({limit: 50});
    const payload = response.data || {};
    state.events = Array.isArray(payload.events) ? payload.events : [];
    state.jobs = Array.isArray(payload.jobs) ? payload.jobs : [];
    renderListings();
    if (!quiet) showStatus("Listings are up to date.", "success");
  } catch (error) {
    renderListings();
    showStatus(messageFrom(error, "Unable to load opportunities right now."), "error");
  } finally {
    refs.refreshButton.disabled = false;
  }
}

function openRegistrationGuide() {
  if (typeof refs.registerDialog.showModal === "function") {
    refs.registerDialog.showModal();
    return;
  }
  showStatus("Register in VolunteersApp first, then return here to sign in and apply.", "info");
}

function isAppleMobileDevice() {
  const userAgent = navigator.userAgent || "";
  const iPadDesktopMode = navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1;
  return /iPad|iPhone|iPod/i.test(userAgent) || iPadDesktopMode;
}

function openInstalledIosApp() {
  if (!isAppleMobileDevice()) {
    showStatus("Open installed iOS app works only on an iPhone or iPad with VolunteersApp already installed. Open this page on that device, or use Download for iOS.", "info");
    return;
  }

  if (refs.registerDialog.open) refs.registerDialog.close();
  showStatus("Opening VolunteersApp. If it does not open, install the iOS beta from TestFlight and try again.", "info");
  window.location.assign("volunteersapp://");
  window.setTimeout(() => {
    if (document.visibilityState === "visible") {
      showStatus("VolunteersApp did not open. Confirm that the iOS beta is installed, then try again or use Download for iOS.", "info");
    }
  }, 1500);
}

async function applyToListing(type, id, button) {
  if (!state.user) {
    openRegistrationGuide();
    return;
  }
  const key = `${type}:${id}`;
  if (state.applying.has(key)) return;
  state.applying.add(key);
  button.disabled = true;
  button.textContent = "Sending application...";
  try {
    await requireAppCheckToken();
    if (type === "EVENT") {
      await applyForEvent({eventId: id});
    } else {
      await applyForJob({jobId: id});
    }
    showStatus("Application sent. You can track updates here or in VolunteersApp.", "success");
    await loadListings({quiet: true});
  } catch (error) {
    showStatus(messageFrom(error, "Your application could not be submitted."), "error");
  } finally {
    state.applying.delete(key);
    renderListings();
  }
}

refs.showLoginButton.addEventListener("click", () => {
  refs.loginForm.classList.toggle("is-open");
  if (refs.loginForm.classList.contains("is-open")) refs.loginEmail.focus();
});

refs.loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const email = refs.loginEmail.value.trim();
  const password = refs.loginPassword.value;
  if (!email || !password) {
    showStatus("Enter your email and password to sign in.", "error");
    return;
  }
  refs.loginButton.disabled = true;
  showStatus("Signing in...", "info");
  try {
    await requireAppCheckToken();
    await signInWithEmailAndPassword(auth, email, password);
  } catch (error) {
    showStatus(messageFrom(error, "Unable to sign in."), "error");
  } finally {
    refs.loginButton.disabled = false;
  }
});

refs.signOutButton.addEventListener("click", async () => {
  await signOut(auth);
  showStatus("Signed out.", "info");
});
refs.eventsTab.addEventListener("click", () => selectTab("events"));
refs.jobsTab.addEventListener("click", () => selectTab("jobs"));
refs.refreshButton.addEventListener("click", () => loadListings());
refs.closeDialogButton.addEventListener("click", () => refs.registerDialog.close());
refs.registerDialog.addEventListener("click", (event) => {
  if (event.target === refs.registerDialog) refs.registerDialog.close();
});

document.querySelectorAll("[data-action='open-installed-ios-app']").forEach((button) => {
  button.addEventListener("click", openInstalledIosApp);
});

document.addEventListener("click", (event) => {
  const button = event.target.closest("button[data-action='apply']");
  if (!button) return;
  applyToListing(button.dataset.type, button.dataset.id, button);
});

onAuthStateChanged(auth, async (user) => {
  state.user = user || null;
  renderSession();
  await loadListings({quiet: Boolean(user)});
});

selectTab("events");
renderSession();
renderListings();
