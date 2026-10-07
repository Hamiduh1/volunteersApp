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
import {
  Timestamp,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  getFirestore,
  limit,
  query,
  serverTimestamp,
  setDoc,
  where,
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-firestore.js";

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
const db = getFirestore(app);

async function requireAppCheckToken() {
  try {
    await getToken(appCheck);
  } catch (error) {
    throw new Error("Website security verification could not start. Refresh the page and try again.");
  }
}

const state = {
  organizerUid: "",
  organizerName: "",
  organizerEmail: "",
  currentEventId: "",
  currentEvents: [],
  currentApplicants: [],
  loadingEvents: false,
  loadingApplicants: false,
  savingEvent: false,
  updatingApplicantIds: new Set(),
};

const refs = {
  authPanel: document.querySelector("#auth-panel"),
  portalPanel: document.querySelector("#portal-panel"),
  sessionChip: document.querySelector("#session-chip"),
  portalStatus: document.querySelector("#portal-status"),
  loginForm: document.querySelector("#login-form"),
  loginButton: document.querySelector("#login-button"),
  topSummary: document.querySelector("#organizer-top-summary"),
  dashboardHero: document.querySelector("#organizer-dashboard-hero"),
  hostedEventsPanel: document.querySelector("#organizer-hosted-events-panel"),
  applicantsPanel: document.querySelector("#organizer-applicants-panel"),
  refreshPortalButton: document.querySelector("#refresh-portal-button"),
  signOutButton: document.querySelector("#sign-out-button"),
  eventForm: document.querySelector("#event-form"),
  composerTitle: document.querySelector("#composer-title"),
  saveEventButton: document.querySelector("#save-event-button"),
  cancelEditButton: document.querySelector("#cancel-edit-button"),
  eventsList: document.querySelector("#events-list"),
  applicantsList: document.querySelector("#applicants-list"),
  applicantFilters: document.querySelector("#applicant-filters"),
  applicantEventFilter: document.querySelector("#applicant-event-filter"),
  eventTitle: document.querySelector("#event-title"),
  eventDescription: document.querySelector("#event-description"),
  eventCategory: document.querySelector("#event-category"),
  eventLocationName: document.querySelector("#event-location-name"),
  eventLocationAddress: document.querySelector("#event-location-address"),
  eventRequirements: document.querySelector("#event-requirements"),
  eventContactInfo: document.querySelector("#event-contact"),
  eventDateTime: document.querySelector("#event-date-time"),
  eventVolunteerLimit: document.querySelector("#event-volunteer-limit"),
  eventPayment: document.querySelector("#event-payment"),
  eventDescriptionCount: document.querySelector("#event-description-count"),
  overviewEvents: document.querySelector("#overview-events"),
  overviewUpcoming: document.querySelector("#overview-upcoming"),
  overviewPending: document.querySelector("#overview-pending"),
};

function showStatus(message, tone = "info") {
  if (!refs.portalStatus) return;
  if (!message) {
    refs.portalStatus.innerHTML = "";
    return;
  }
  refs.portalStatus.innerHTML = `<div class="organizer-alert ${tone}">${escapeHtml(message)}</div>`;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function normalizeText(value) {
  return String(value ?? "").trim();
}

function timestampToDate(value) {
  if (!value) return null;
  if (typeof value.toDate === "function") return value.toDate();
  if (value instanceof Date) return value;
  return null;
}

function formatDateTime(value) {
  const date = timestampToDate(value);
  if (!date) return "Date unavailable";
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

function toDatetimeLocalValue(value) {
  const date = value instanceof Date ? value : timestampToDate(value);
  if (!date) return "";
  const local = new Date(date.getTime() - (date.getTimezoneOffset() * 60000));
  return local.toISOString().slice(0, 16);
}

function parseDateTimeLocal(value) {
  const clean = normalizeText(value);
  if (!clean) return null;
  const date = new Date(clean);
  return Number.isNaN(date.getTime()) ? null : date;
}

function applicantStatusLabel(status) {
  const clean = normalizeText(status).toUpperCase();
  if (!clean) return "Unknown";
  return clean
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function isPendingStatus(status) {
  const clean = normalizeText(status).toUpperCase();
  return clean === "PENDING" || clean === "VIEWED" || clean === "WAITLISTED" || clean === "";
}

async function fetchOrganizerIdentity(uid) {
  const [organizerSnap, userSnap] = await Promise.all([
    getDoc(doc(db, "organizers", uid)),
    getDoc(doc(db, "users", uid)),
  ]);

  if (!organizerSnap.exists()) {
    throw new Error("This portal is restricted to organizer accounts that already have an organizer profile.");
  }

  const organizerData = organizerSnap.data() ?? {};
  const userData = userSnap.data() ?? {};
  const displayName = normalizeText(
    organizerData.organizationName ||
      organizerData.name ||
      userData.organizationName ||
      userData.name ||
      userData.username ||
      auth.currentUser?.email ||
      "Organizer"
  );

  return {
    name: displayName,
    email: normalizeText(userData.email || auth.currentUser?.email || ""),
  };
}

async function fetchHostedEvents(uid) {
  const queries = [
    query(collection(db, "events"), where("organizerId", "==", uid), limit(120)),
    query(collection(db, "events"), where("organizerUid", "==", uid), limit(120)),
  ];

  const byId = new Map();
  for (const currentQuery of queries) {
    const snapshot = await getDocs(currentQuery);
    snapshot.forEach((docSnap) => {
      byId.set(docSnap.id, {id: docSnap.id, ...docSnap.data()});
    });
  }

  return Array.from(byId.values()).sort((left, right) => {
    const leftDate = timestampToDate(left.eventDateTime) ?? timestampToDate(left.createdAt) ?? new Date(8640000000000000);
    const rightDate = timestampToDate(right.eventDateTime) ?? timestampToDate(right.createdAt) ?? new Date(8640000000000000);
    return leftDate - rightDate;
  });
}

async function fetchApplicantsForEvents(events) {
  const items = [];
  for (const event of events) {
    const eventId = normalizeText(event.id);
    if (!eventId) continue;
    const applicationsRef = collection(db, "events", eventId, "applications");
    const snapshot = await getDocs(applicationsRef);
    snapshot.forEach((docSnap) => {
      const data = docSnap.data() ?? {};
      items.push({
        id: `${eventId}-${docSnap.id}`,
        documentId: docSnap.id,
        eventId,
        eventTitle: normalizeText(event.title) || "Event",
        volunteerName: normalizeText(data.volunteerName || data.name || data.applicantName || "Volunteer"),
        volunteerEmail: normalizeText(data.volunteerEmail || data.email || data.userEmail || ""),
        status: normalizeText(data.status || "PENDING").toUpperCase(),
        appliedAt: data.appliedAt || data.appliedDate || data.createdAt || data.timestamp || null,
      });
    });
  }

  return items.sort((left, right) => {
    const leftDate = timestampToDate(left.appliedAt) ?? new Date(0);
    const rightDate = timestampToDate(right.appliedAt) ?? new Date(0);
    return rightDate - leftDate;
  });
}

function renderSessionChip() {
  if (!refs.sessionChip) return;
  if (!state.organizerUid) {
    refs.sessionChip.classList.add("organizer-hidden");
    refs.sessionChip.textContent = "";
    return;
  }
  const detail = state.organizerEmail ? ` · ${state.organizerEmail}` : "";
  refs.sessionChip.textContent = `${state.organizerName}${detail}`;
  refs.sessionChip.classList.remove("organizer-hidden");
}

function renderAuthState() {
  const signedIn = Boolean(state.organizerUid);
  document.body.classList.toggle("organizer-authenticated", signedIn);
  refs.authPanel.classList.toggle("organizer-hidden", signedIn);
  renderSessionChip();
  renderOverview();
}

function renderOverview() {
  const signedIn = Boolean(state.organizerUid);
  const now = new Date();
  const hostedEventCount = state.currentEvents.length;
  const upcomingEventCount = state.currentEvents.filter((event) => {
    const eventDate = timestampToDate(event.eventDateTime);
    const status = normalizeText(event.status).toUpperCase();
    return Boolean(eventDate && eventDate >= now && status !== "CLOSED" && status !== "COMPLETED");
  }).length;
  // Event documents carry the authoritative participant total while individual
  // application records provide the review status. This keeps the nav current
  // during the short period before every application record is returned.
  const recordedApplicantCount = state.currentEvents.reduce((total, event) => (
    total + Math.max(0, Number(event.participantsCount) || 0)
  ), 0);
  const totalApplicantCount = Math.max(recordedApplicantCount, state.currentApplicants.length);
  const reviewedApplicantCount = state.currentApplicants.filter((item) => !isPendingStatus(item.status)).length;
  const pendingApplicantCount = Math.max(0, totalApplicantCount - reviewedApplicantCount);

  const values = {
    events: signedIn ? String(hostedEventCount) : "--",
    upcoming: signedIn ? String(upcomingEventCount) : "--",
    pending: signedIn ? String(pendingApplicantCount) : "--",
  };
  Object.entries(values).forEach(([key, value]) => {
    document.querySelectorAll(`[data-overview-count="${key}"]`).forEach((element) => {
      element.textContent = value;
    });
  });
}

function eventState(event) {
  const status = normalizeText(event.status).toUpperCase();
  if (status === "CLOSED" || status === "COMPLETED" || event.isActive === false) {
    return {label: status === "COMPLETED" ? "Completed" : "Closed", tone: "closed"};
  }
  const eventDate = timestampToDate(event.eventDateTime);
  if (eventDate && eventDate < new Date()) {
    return {label: "Past", tone: "past"};
  }
  return {label: "Upcoming", tone: "upcoming"};
}

function applicantStateTone(status) {
  const clean = normalizeText(status).toUpperCase();
  if (isPendingStatus(clean)) return "pending";
  if (clean === "APPROVED") return "approved";
  return "rejected";
}

function eventSummaryMarkup(event) {
  const payment = Number(event.payment ?? event.eventFee ?? 0);
  const limit = Number(event.volunteerLimit ?? 0);
  const applied = Number(event.participantsCount ?? 0);
  const status = eventState(event);
  return `
    <div class="organizer-event-item">
      <div class="organizer-event-heading">
        <h3>${escapeHtml(event.title || "Untitled Event")}</h3>
        <span class="organizer-state ${escapeHtml(status.tone)}">${escapeHtml(status.label)}</span>
      </div>
      <div>${escapeHtml(event.locationName || event.locationAddress || "Location unavailable")}</div>
      <div class="organizer-mini-meta">
        <span class="organizer-pill">${escapeHtml(formatDateTime(event.eventDateTime))}</span>
        <span class="organizer-pill">Limit: ${escapeHtml(limit || 0)}</span>
        <span class="organizer-pill">Applied: ${escapeHtml(applied || 0)}</span>
        <span class="organizer-pill">Payment: $${escapeHtml(payment.toFixed(2))}</span>
      </div>
      <div class="organizer-button-row" style="margin-top: 12px;">
        <button class="organizer-btn secondary" type="button" data-action="edit" data-event-id="${escapeHtml(event.id)}">Edit</button>
        <button class="organizer-btn ghost" type="button" data-action="filter-applicants" data-event-id="${escapeHtml(event.id)}">Applicants</button>
        <button class="organizer-btn danger" type="button" data-action="delete" data-event-id="${escapeHtml(event.id)}">Delete</button>
      </div>
    </div>
  `;
}

function renderEvents() {
  if (state.loadingEvents) {
    refs.eventsList.innerHTML = '<div class="organizer-empty">Loading hosted events...</div>';
    return;
  }
  if (!state.organizerUid) {
    refs.eventsList.innerHTML = '<div class="organizer-empty">Sign in to load your hosted events.</div>';
    return;
  }
  if (state.currentEvents.length === 0) {
    refs.eventsList.innerHTML = '<div class="organizer-empty">No hosted events yet. Use the event editor to publish your first listing.</div>';
    renderOverview();
    return;
  }
  refs.eventsList.innerHTML = state.currentEvents.map(eventSummaryMarkup).join("");
  renderOverview();
}

function renderApplicantFilters() {
  if (!state.organizerUid || state.currentApplicants.length === 0) {
    refs.applicantFilters.classList.add("organizer-hidden");
    refs.applicantEventFilter.innerHTML = '<option value="">All Events</option>';
    return;
  }

  const grouped = new Map();
  state.currentApplicants.forEach((item) => {
    if (!grouped.has(item.eventId)) {
      grouped.set(item.eventId, item.eventTitle);
    }
  });

  const options = Array.from(grouped.entries())
    .sort((left, right) => left[1].localeCompare(right[1]))
    .map(([id, title]) => `<option value="${escapeHtml(id)}">${escapeHtml(title)}</option>`)
    .join("");

  const current = refs.applicantEventFilter.value;
  refs.applicantEventFilter.innerHTML = `<option value="">All Events</option>${options}`;
  refs.applicantEventFilter.value = grouped.has(current) ? current : "";
  refs.applicantFilters.classList.remove("organizer-hidden");
}

function applicantMarkup(item) {
  const pending = isPendingStatus(item.status);
  const updating = state.updatingApplicantIds.has(item.id);
  const statusLabel = applicantStatusLabel(item.status);
  return `
    <div class="organizer-applicant-item">
      <div class="organizer-event-heading">
        <h3>${escapeHtml(item.volunteerName)}</h3>
        <span class="organizer-state ${escapeHtml(applicantStateTone(item.status))}">${escapeHtml(statusLabel)}</span>
      </div>
      <div>${escapeHtml(item.eventTitle)}</div>
      <div class="organizer-meta">
        ${item.volunteerEmail ? `<span class="organizer-pill">${escapeHtml(item.volunteerEmail)}</span>` : ""}
        <span class="organizer-pill">Applied: ${escapeHtml(formatDateTime(item.appliedAt))}</span>
      </div>
      ${
        pending
          ? `<div class="organizer-button-row" style="margin-top: 12px;">
              <button class="organizer-btn primary" type="button" data-action="approve" data-applicant-id="${escapeHtml(item.id)}" ${updating ? "disabled" : ""}>Approve</button>
              <button class="organizer-btn secondary" type="button" data-action="reject" data-applicant-id="${escapeHtml(item.id)}" ${updating ? "disabled" : ""}>Reject</button>
            </div>`
          : ""
      }
    </div>
  `;
}

function renderApplicants() {
  renderApplicantFilters();
  if (state.loadingApplicants) {
    refs.applicantsList.innerHTML = '<div class="organizer-empty">Loading applicants...</div>';
    return;
  }
  if (!state.organizerUid) {
    refs.applicantsList.innerHTML = '<div class="organizer-empty">Sign in to review event applicants.</div>';
    return;
  }
  const filterEventId = refs.applicantEventFilter.value;
  const items = filterEventId
    ? state.currentApplicants.filter((item) => item.eventId === filterEventId)
    : state.currentApplicants;
  if (items.length === 0) {
    refs.applicantsList.innerHTML = '<div class="organizer-empty">No applicants found for the current filter.</div>';
    renderOverview();
    return;
  }
  refs.applicantsList.innerHTML = items.map(applicantMarkup).join("");
  renderOverview();
}

function updateDescriptionCount() {
  if (!refs.eventDescriptionCount) return;
  const count = refs.eventDescription?.value.length ?? 0;
  refs.eventDescriptionCount.textContent = `${count.toLocaleString()} / 2,000`;
}

function resetComposer() {
  state.currentEventId = "";
  refs.composerTitle.textContent = "Create Event";
  refs.saveEventButton.textContent = "Create Event";
  refs.cancelEditButton.classList.add("organizer-hidden");
  refs.eventForm.reset();
  refs.eventCategory.value = "Community";
  refs.eventVolunteerLimit.value = "20";
  refs.eventPayment.value = "0.00";
  const defaultDate = new Date(Date.now() + (60 * 60 * 1000));
  refs.eventDateTime.value = toDatetimeLocalValue(defaultDate);
  updateDescriptionCount();
}

function loadEventIntoComposer(eventId) {
  const event = state.currentEvents.find((entry) => entry.id === eventId);
  if (!event) return;
  state.currentEventId = eventId;
  refs.composerTitle.textContent = "Edit Event";
  refs.saveEventButton.textContent = "Save Changes";
  refs.cancelEditButton.classList.remove("organizer-hidden");
  refs.eventTitle.value = event.title || "";
  refs.eventDescription.value = event.description || "";
  refs.eventCategory.value = event.category || "Community";
  refs.eventLocationName.value = event.locationName || "";
  refs.eventLocationAddress.value = event.locationAddress || "";
  refs.eventRequirements.value = event.requirements || "";
  refs.eventContactInfo.value = event.contactInfo || "";
  refs.eventDateTime.value = toDatetimeLocalValue(event.eventDateTime);
  refs.eventVolunteerLimit.value = String(event.volunteerLimit ?? 20);
  refs.eventPayment.value = Number(event.payment ?? event.eventFee ?? 0).toFixed(2);
  updateDescriptionCount();
  window.scrollTo({top: 0, behavior: "smooth"});
}

async function refreshPortalData() {
  if (!state.organizerUid) return false;
  state.loadingEvents = true;
  state.loadingApplicants = true;
  renderEvents();
  renderApplicants();
  try {
    const events = await fetchHostedEvents(state.organizerUid);
    state.currentEvents = events;
    state.loadingEvents = false;
    renderEvents();
    const applicants = await fetchApplicantsForEvents(events);
    state.currentApplicants = applicants;
    state.loadingApplicants = false;
    renderApplicants();
    return true;
  } catch (error) {
    state.loadingEvents = false;
    state.loadingApplicants = false;
    renderEvents();
    renderApplicants();
    showStatus(error.message || "Unable to load organizer data.", "error");
    return false;
  }
}

async function saveHostedEvent() {
  const title = normalizeText(refs.eventTitle.value);
  const description = normalizeText(refs.eventDescription.value);
  const category = normalizeText(refs.eventCategory.value) || "Community";
  const locationName = normalizeText(refs.eventLocationName.value);
  const locationAddress = normalizeText(refs.eventLocationAddress.value);
  const requirements = normalizeText(refs.eventRequirements.value);
  const contactInfo = normalizeText(refs.eventContactInfo.value);
  const eventDate = parseDateTimeLocal(refs.eventDateTime.value);
  const volunteerLimit = Number(refs.eventVolunteerLimit.value);
  const payment = Number(refs.eventPayment.value);

  if (!title || !description || !locationName || !eventDate) {
    throw new Error("Title, description, location, and date are required.");
  }
  if (!Number.isFinite(volunteerLimit) || volunteerLimit <= 0) {
    throw new Error("Volunteer limit must be a positive number.");
  }
  if (!Number.isFinite(payment) || payment < 0) {
    throw new Error("Payment must be zero or a positive number.");
  }

  const eventRef = state.currentEventId
    ? doc(db, "events", state.currentEventId)
    : doc(collection(db, "events"));
  const hostedRef = doc(db, "users", state.organizerUid, "hostedEvents", eventRef.id);
  const existingEvent = state.currentEvents.find((item) => item.id === state.currentEventId) || {};
  const timestamp = Timestamp.fromDate(eventDate);

  const eventPayload = {
    title,
    titleLowercase: title.toLowerCase(),
    description,
    category,
    locationName,
    locationAddress,
    eventDateTime: timestamp,
    eventTimestamp: timestamp,
    volunteerLimit,
    participantsCount: Number(existingEvent.participantsCount ?? 0),
    payment,
    eventFee: payment,
    requirements,
    contactInfo,
    isActive: true,
    closeEntries: false,
    opportunityType: "EVENT",
    status: "OPEN",
    organizerId: state.organizerUid,
    organizerUid: state.organizerUid,
    organizerName: state.organizerName,
    lastUpdatedAt: serverTimestamp(),
  };

  if (!state.currentEventId) {
    eventPayload.createdAt = serverTimestamp();
  }

  await setDoc(eventRef, eventPayload, {merge: true});
  await setDoc(hostedRef, {
    eventId: eventRef.id,
    eventName: title,
    title,
    location: locationName || locationAddress,
    status: "OPEN",
    timestamp: serverTimestamp(),
    eventDateTime: timestamp,
  }, {merge: true});

  state.currentEventId = eventRef.id;
}

async function deleteHostedEvent(eventId) {
  const confirmed = window.confirm("Delete this event? This removes the hosted event from your organizer list.");
  if (!confirmed) return;
  await deleteDoc(doc(db, "events", eventId));
  await deleteDoc(doc(db, "users", state.organizerUid, "hostedEvents", eventId)).catch(() => {});
  if (state.currentEventId === eventId) {
    resetComposer();
  }
  showStatus("Event removed.", "info");
  await refreshPortalData();
}

async function updateApplicantStatus(applicantId, status) {
  const applicant = state.currentApplicants.find((item) => item.id === applicantId);
  if (!applicant) return;
  state.updatingApplicantIds.add(applicantId);
  renderApplicants();
  try {
    await setDoc(doc(db, "events", applicant.eventId, "applications", applicant.documentId), {
      status,
      lastUpdatedAt: serverTimestamp(),
    }, {merge: true});
    showStatus(`Application marked as ${applicantStatusLabel(status)}.`, "info");
    await refreshPortalData();
  } catch (error) {
    showStatus(error.message || "Unable to update application status.", "error");
  } finally {
    state.updatingApplicantIds.delete(applicantId);
    renderApplicants();
  }
}

function attachEventDelegates() {
  refs.eventsList.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const action = button.dataset.action;
    const eventId = normalizeText(button.dataset.eventId);
    if (!eventId) return;

    if (action === "edit") {
      loadEventIntoComposer(eventId);
    } else if (action === "filter-applicants") {
      refs.applicantEventFilter.value = eventId;
      renderApplicants();
      document.querySelector("#applicants-list")?.scrollIntoView({behavior: "smooth", block: "start"});
    } else if (action === "delete") {
      deleteHostedEvent(eventId).catch((error) => {
        showStatus(error.message || "Unable to delete event.", "error");
      });
    }
  });

  refs.applicantsList.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const action = button.dataset.action;
    const applicantId = normalizeText(button.dataset.applicantId);
    if (!applicantId) return;

    if (action === "approve") {
      updateApplicantStatus(applicantId, "APPROVED");
    } else if (action === "reject") {
      updateApplicantStatus(applicantId, "REJECTED");
    }
  });
}

refs.loginForm?.addEventListener("submit", async (event) => {
  event.preventDefault();
  const email = normalizeText(new FormData(refs.loginForm).get("email"));
  const password = String(new FormData(refs.loginForm).get("password") || "");
  if (!email || !password) {
    showStatus("Email and password are required.", "error");
    return;
  }
  refs.loginButton.disabled = true;
  showStatus("Signing in...", "info");
  try {
    await requireAppCheckToken();
    await signInWithEmailAndPassword(auth, email, password);
  } catch (error) {
    showStatus(error.message || "Unable to sign in.", "error");
  } finally {
    refs.loginButton.disabled = false;
  }
});

refs.signOutButton?.addEventListener("click", async () => {
  await signOut(auth);
  showStatus("Signed out.", "info");
});

refs.refreshPortalButton?.addEventListener("click", async () => {
  if (!state.organizerUid) return;
  refs.refreshPortalButton.disabled = true;
  showStatus("Refreshing your event data...", "info");
  try {
    const didRefresh = await refreshPortalData();
    if (didRefresh) {
      showStatus("Event data is up to date.", "info");
    }
  } finally {
    refs.refreshPortalButton.disabled = false;
  }
});

refs.eventDescription?.addEventListener("input", updateDescriptionCount);

refs.eventForm?.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!state.organizerUid) {
    showStatus("Please sign in first.", "error");
    return;
  }
  const isEditMode = Boolean(state.currentEventId);
  state.savingEvent = true;
  refs.saveEventButton.disabled = true;
  showStatus(isEditMode ? "Saving event changes..." : "Creating event...", "info");
  try {
    await saveHostedEvent();
    showStatus(isEditMode ? "Event saved." : "Event created.", "info");
    resetComposer();
    await refreshPortalData();
  } catch (error) {
    showStatus(error.message || "Unable to save event.", "error");
  } finally {
    state.savingEvent = false;
    refs.saveEventButton.disabled = false;
  }
});

refs.cancelEditButton?.addEventListener("click", () => {
  resetComposer();
  showStatus("Edit mode cancelled.", "info");
});

refs.applicantEventFilter?.addEventListener("change", () => {
  renderApplicants();
});

attachEventDelegates();
resetComposer();

onAuthStateChanged(auth, async (user) => {
  state.organizerUid = "";
  state.organizerName = "";
  state.organizerEmail = "";
  state.currentEvents = [];
  state.currentApplicants = [];
  state.currentEventId = "";
  renderAuthState();
  renderEvents();
  renderApplicants();
  resetComposer();

  if (!user) {
    return;
  }

  showStatus("Checking organizer access...", "info");
  try {
    await requireAppCheckToken();
    const identity = await fetchOrganizerIdentity(user.uid);
    state.organizerUid = user.uid;
    state.organizerName = identity.name;
    state.organizerEmail = identity.email;
    renderAuthState();
    showStatus("Organizer access granted.", "info");
    await refreshPortalData();
  } catch (error) {
    await signOut(auth).catch(() => {});
    showStatus(error.message || "Organizer access is not available for this account.", "error");
  }
});
