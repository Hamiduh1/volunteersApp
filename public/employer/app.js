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
  collectionGroup,
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
  employerUid: "",
  employerName: "",
  employerEmail: "",
  organizationName: "",
  profileDescription: "",
  currentJobId: "",
  currentJobs: [],
  currentApplications: [],
  loadingJobs: false,
  loadingApplications: false,
  savingJob: false,
  savingProfile: false,
  updatingApplicationIds: new Set(),
  togglingJobIds: new Set(),
};

const refs = {
  authPanel: document.querySelector("#auth-panel"),
  portalPanel: document.querySelector("#portal-panel"),
  sessionChip: document.querySelector("#session-chip"),
  portalStatus: document.querySelector("#portal-status"),
  loginForm: document.querySelector("#login-form"),
  loginButton: document.querySelector("#login-button"),
  topSummary: document.querySelector("#employer-top-summary"),
  dashboardHero: document.querySelector("#employer-dashboard-hero"),
  postedJobsPanel: document.querySelector("#employer-posted-jobs-panel"),
  applicationsPanel: document.querySelector("#employer-applications-panel"),
  refreshPortalButton: document.querySelector("#refresh-portal-button"),
  signOutButton: document.querySelector("#sign-out-button"),
  profileForm: document.querySelector("#profile-form"),
  saveProfileButton: document.querySelector("#save-profile-button"),
  jobForm: document.querySelector("#job-form"),
  composerTitle: document.querySelector("#composer-title"),
  saveJobButton: document.querySelector("#save-job-button"),
  cancelEditButton: document.querySelector("#cancel-edit-button"),
  jobsList: document.querySelector("#jobs-list"),
  applicationsList: document.querySelector("#applications-list"),
  applicationFilters: document.querySelector("#application-filters"),
  applicationJobFilter: document.querySelector("#application-job-filter"),
  profileName: document.querySelector("#profile-name"),
  profileEmail: document.querySelector("#profile-email"),
  profileOrganization: document.querySelector("#profile-organization"),
  profileDescription: document.querySelector("#profile-description"),
  jobOrganizationName: document.querySelector("#job-organization-name"),
  jobCategory: document.querySelector("#job-category"),
  jobOpportunityTitle: document.querySelector("#job-opportunity-title"),
  jobRoleTitle: document.querySelector("#job-role-title"),
  jobLocation: document.querySelector("#job-location"),
  jobVolunteersNeeded: document.querySelector("#job-volunteers-needed"),
  jobScheduledDate: document.querySelector("#job-scheduled-date"),
  jobDescription: document.querySelector("#job-description"),
  jobDescriptionCount: document.querySelector("#job-description-count"),
  overviewPosted: document.querySelector("#overview-posted"),
  overviewOpen: document.querySelector("#overview-open"),
  overviewPending: document.querySelector("#overview-pending"),
};

function showStatus(message, tone = "info") {
  if (!refs.portalStatus) return;
  if (!message) {
    refs.portalStatus.innerHTML = "";
    return;
  }
  refs.portalStatus.innerHTML = `<div class="employer-alert ${tone}">${escapeHtml(message)}</div>`;
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

function firstNonEmpty(...values) {
  for (const value of values) {
    const clean = normalizeText(value);
    if (clean) return clean;
  }
  return "";
}

function timestampToDate(value) {
  if (!value) return null;
  if (typeof value.toDate === "function") return value.toDate();
  if (value instanceof Date) return value;
  if (typeof value === "number" && Number.isFinite(value) && value > 0) {
    return new Date(value > 1_000_000_000_000 ? value : value * 1000);
  }
  if (typeof value === "string") {
    const asNumber = Number(value);
    if (Number.isFinite(asNumber) && asNumber > 0) {
      return new Date(asNumber > 1_000_000_000_000 ? asNumber : asNumber * 1000);
    }
    const parsed = new Date(value);
    if (!Number.isNaN(parsed.getTime())) return parsed;
  }
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

function applicationStatusLabel(status) {
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

function normalizeDocId(raw, collectionName) {
  const clean = normalizeText(raw);
  if (!clean) return "";
  const segments = clean.split("/").filter(Boolean);
  const collectionIndex = segments.indexOf(collectionName);
  if (collectionIndex >= 0 && segments.length > collectionIndex + 1) {
    return segments[collectionIndex + 1];
  }
  return segments.at(-1) || clean;
}

function jobIdFromPath(path) {
  const segments = String(path ?? "").split("/").filter(Boolean);
  return segments.length >= 2 && segments[0] === "jobs" ? segments[1] : "";
}

function applicationDocumentIdFromPath(path) {
  const segments = String(path ?? "").split("/").filter(Boolean);
  return segments.length >= 4 && segments[0] === "jobs" && segments[2] === "applications" ? segments[3] : "";
}

function isJobApplication(path, data) {
  return Boolean(normalizeText(data?.jobId)) || Boolean(jobIdFromPath(path));
}

function resolveScheduledDate(job) {
  const dateString = normalizeText(job.date);
  const timeString = normalizeText(job.time) || "09:00 AM";
  if (dateString) {
    const formats = [
      `${dateString} ${timeString}`,
      `${dateString}T${timeString}`,
    ];
    for (const candidate of formats) {
      const parsed = new Date(candidate);
      if (!Number.isNaN(parsed.getTime())) return parsed;
    }
  }
  return (
    timestampToDate(job.applicationDeadline) ||
    timestampToDate(job.eventDateTime) ||
    timestampToDate(job.postedDate) ||
    new Date(Date.now() + (24 * 60 * 60 * 1000))
  );
}

async function fetchEmployerIdentity(uid) {
  const [employerSnap, userSnap] = await Promise.all([
    getDoc(doc(db, "employers", uid)),
    getDoc(doc(db, "users", uid)),
  ]);

  if (!employerSnap.exists()) {
    throw new Error("This portal is restricted to employer accounts that already have an employer profile.");
  }

  const employerData = employerSnap.data() ?? {};
  const userData = userSnap.data() ?? {};

  return {
    name: firstNonEmpty(
      userData.name,
      userData.username,
      employerData.contactName,
      employerData.organizationName,
      auth.currentUser?.email,
      "Employer"
    ),
    email: firstNonEmpty(userData.email, employerData.contactEmail, auth.currentUser?.email),
    organizationName: firstNonEmpty(
      employerData.organizationName,
      userData.organizationName,
      employerData.companyName
    ),
    description: firstNonEmpty(employerData.description, employerData.about),
  };
}

async function fetchPostedJobs(uid) {
  const queries = [
    query(collection(db, "jobs"), where("employerUid", "==", uid), limit(120)),
    query(collection(db, "jobs"), where("employerId", "==", uid), limit(120)),
  ];

  const byId = new Map();
  let lastError = null;

  for (const currentQuery of queries) {
    try {
      const snapshot = await getDocs(currentQuery);
      snapshot.forEach((docSnap) => {
        const data = docSnap.data() ?? {};
        if (Boolean(data.isDeleted)) return;
        byId.set(docSnap.id, {
          id: docSnap.id,
          title: firstNonEmpty(data.title, data.name),
          organizationName: firstNonEmpty(data.organizationName, data.companyName),
          jobTitle: firstNonEmpty(data.jobTitle, data.roleTitle),
          employerUid: firstNonEmpty(data.employerUid),
          employerId: firstNonEmpty(data.employerId),
          employerName: firstNonEmpty(data.employerName, data.companyName),
          description: firstNonEmpty(data.description, data.details),
          locationString: firstNonEmpty(data.locationString, data.locationName, data.location),
          locationName: firstNonEmpty(data.locationName, data.locationString, data.location),
          category: firstNonEmpty(data.category),
          date: firstNonEmpty(data.date),
          time: firstNonEmpty(data.time),
          volunteersNeeded: Number(data.volunteersNeeded ?? data.totalSlots ?? data.slots ?? 0),
          postedDate: data.postedDate ?? data.createdAt ?? data.timestamp ?? null,
          applicationDeadline: data.applicationDeadline ?? data.deadline ?? data.eventDateTime ?? null,
          eventDateTime: data.eventDateTime ?? data.eventTimestamp ?? null,
          status: firstNonEmpty(data.status) || "open",
          totalSlots: Number(data.totalSlots ?? data.volunteersNeeded ?? data.slots ?? 0),
          slotsFilled: Number(data.slotsFilled ?? 0),
          applicantsCount: Number(data.applicantsCount ?? data.applicationsCount ?? 0),
        });
      });
    } catch (error) {
      lastError = error;
    }
  }

  if (byId.size === 0 && lastError) {
    throw lastError;
  }

  return Array.from(byId.values()).sort((left, right) => {
    const leftDate = timestampToDate(left.postedDate) ?? timestampToDate(left.applicationDeadline) ?? new Date(0);
    const rightDate = timestampToDate(right.postedDate) ?? timestampToDate(right.applicationDeadline) ?? new Date(0);
    return rightDate - leftDate;
  });
}

function parseApplicationDoc(docSnap, jobTitleMap) {
  const data = docSnap.data() ?? {};
  const sourcePath = docSnap.ref.path;
  const jobId = normalizeDocId(firstNonEmpty(data.jobId, jobIdFromPath(sourcePath)), "jobs");
  if (!jobId) return null;

  const subDocumentId = applicationDocumentIdFromPath(sourcePath);
  const documentId = subDocumentId || docSnap.id;
  const applicationId = firstNonEmpty(data.applicationId, data.id, documentId);
  const volunteerId = firstNonEmpty(data.userId, data.volunteerUid, data.volunteerId, data.applicantId);
  const volunteerEmail = firstNonEmpty(data.volunteerEmail, data.email, data.userEmail);
  const dedupeKey = `${jobId}|${volunteerId || volunteerEmail || applicationId || documentId}`;

  return {
    id: dedupeKey,
    applicationId,
    documentId,
    rootApplicationId: subDocumentId ? firstNonEmpty(data.rootApplicationId) : docSnap.id,
    subDocumentId,
    sourcePaths: [sourcePath],
    jobId,
    jobTitle: firstNonEmpty(data.jobTitle, data.title, jobTitleMap.get(jobId), "Opportunity"),
    volunteerId,
    volunteerName: firstNonEmpty(data.volunteerName, data.name, data.applicantName, data.userName, "Volunteer"),
    volunteerEmail,
    status: firstNonEmpty(data.status, "PENDING").toUpperCase(),
    appliedAt: (
      timestampToDate(data.appliedAt) ||
      timestampToDate(data.appliedDate) ||
      timestampToDate(data.createdAt) ||
      timestampToDate(data.timestamp) ||
      timestampToDate(data.lastUpdatedAt)
    ),
    resumeDownloadUrl: firstNonEmpty(data.resumeDownloadUrl, data.resumeUrl),
    resumeFileName: firstNonEmpty(data.resumeFileName, data.resumeFilename),
  };
}

function mergeApplicationItems(existing, incoming) {
  if (!existing) return incoming;
  const mergedPaths = Array.from(new Set([...(existing.sourcePaths ?? []), ...(incoming.sourcePaths ?? [])]));
  return {
    ...existing,
    applicationId: firstNonEmpty(existing.applicationId, incoming.applicationId),
    rootApplicationId: firstNonEmpty(existing.rootApplicationId, incoming.rootApplicationId),
    subDocumentId: firstNonEmpty(existing.subDocumentId, incoming.subDocumentId),
    documentId: firstNonEmpty(existing.documentId, incoming.documentId),
    jobTitle: firstNonEmpty(existing.jobTitle, incoming.jobTitle, "Opportunity"),
    volunteerId: firstNonEmpty(existing.volunteerId, incoming.volunteerId),
    volunteerName: firstNonEmpty(existing.volunteerName, incoming.volunteerName, "Volunteer"),
    volunteerEmail: firstNonEmpty(existing.volunteerEmail, incoming.volunteerEmail),
    status: firstNonEmpty(incoming.status, existing.status, "PENDING").toUpperCase(),
    appliedAt: incoming.appliedAt || existing.appliedAt || null,
    resumeDownloadUrl: firstNonEmpty(existing.resumeDownloadUrl, incoming.resumeDownloadUrl),
    resumeFileName: firstNonEmpty(existing.resumeFileName, incoming.resumeFileName),
    sourcePaths: mergedPaths,
  };
}

async function fetchManagedApplications(uid, jobs) {
  const queries = [
    query(collection(db, "applications"), where("employerUid", "==", uid), limit(250)),
    query(collection(db, "applications"), where("employerId", "==", uid), limit(250)),
    query(collectionGroup(db, "applications"), where("employerUid", "==", uid), limit(250)),
    query(collectionGroup(db, "applications"), where("employerId", "==", uid), limit(250)),
  ];

  const docsByPath = new Map();
  let lastError = null;

  for (const currentQuery of queries) {
    try {
      const snapshot = await getDocs(currentQuery);
      snapshot.forEach((docSnap) => {
        const data = docSnap.data() ?? {};
        if (!isJobApplication(docSnap.ref.path, data)) return;
        docsByPath.set(docSnap.ref.path, docSnap);
      });
    } catch (error) {
      lastError = error;
    }
  }

  if (docsByPath.size === 0 && jobs.length > 0) {
    for (const job of jobs) {
      if (!job.id) continue;
      try {
        const snapshot = await getDocs(query(collection(db, "jobs", job.id, "applications"), limit(250)));
        snapshot.forEach((docSnap) => {
          const data = docSnap.data() ?? {};
          if (!isJobApplication(docSnap.ref.path, data)) return;
          docsByPath.set(docSnap.ref.path, docSnap);
        });
      } catch (error) {
        if (!lastError) lastError = error;
      }
    }
  }

  if (docsByPath.size === 0 && lastError) {
    throw lastError;
  }

  const jobTitleMap = new Map(
    jobs.map((job) => [job.id, firstNonEmpty(job.title, job.jobTitle, "Opportunity")])
  );
  const merged = new Map();

  for (const docSnap of docsByPath.values()) {
    const item = parseApplicationDoc(docSnap, jobTitleMap);
    if (!item) continue;
    merged.set(item.id, mergeApplicationItems(merged.get(item.id), item));
  }

  return Array.from(merged.values()).sort((left, right) => {
    const leftDate = left.appliedAt ?? new Date(0);
    const rightDate = right.appliedAt ?? new Date(0);
    return rightDate - leftDate;
  });
}

function renderSessionChip() {
  if (!refs.sessionChip) return;
  if (!state.employerUid) {
    refs.sessionChip.classList.add("employer-hidden");
    refs.sessionChip.textContent = "";
    return;
  }
  const detail = state.employerEmail ? ` · ${state.employerEmail}` : "";
  refs.sessionChip.textContent = `${state.employerName}${detail}`;
  refs.sessionChip.classList.remove("employer-hidden");
}

function renderAuthState() {
  const signedIn = Boolean(state.employerUid);
  document.body.classList.toggle("employer-authenticated", signedIn);
  refs.authPanel.classList.toggle("employer-hidden", signedIn);
  renderSessionChip();
  renderOverview();
}

function renderOverview() {
  const signedIn = Boolean(state.employerUid);
  const postedCount = state.currentJobs.length;
  const openCount = state.currentJobs.filter((job) => (
    normalizeText(job.status).toLowerCase() === "open" && job.isActive !== false && job.isDeleted !== true
  )).length;
  // Job documents carry the application total while individual records carry
  // the review status, so keep the nav accurate while reads settle.
  const recordedApplicationCount = state.currentJobs.reduce((total, job) => (
    total + Math.max(0, Number(job.applicantsCount) || 0)
  ), 0);
  const totalApplicationCount = Math.max(recordedApplicationCount, state.currentApplications.length);
  const reviewedApplicationCount = state.currentApplications.filter((item) => !isPendingStatus(item.status)).length;
  const pendingCount = Math.max(0, totalApplicationCount - reviewedApplicationCount);

  const values = {
    posted: signedIn ? String(postedCount) : "--",
    open: signedIn ? String(openCount) : "--",
    pending: signedIn ? String(pendingCount) : "--",
  };
  Object.entries(values).forEach(([key, value]) => {
    document.querySelectorAll(`[data-overview-count="${key}"]`).forEach((element) => {
      element.textContent = value;
    });
  });
}

function jobState(job) {
  const isOpen = normalizeText(job.status).toLowerCase() === "open" && job.isActive !== false && job.isDeleted !== true;
  return isOpen ? {label: "Open", tone: "open"} : {label: "Closed", tone: "closed"};
}

function applicationStateTone(status) {
  const clean = normalizeText(status).toUpperCase();
  if (isPendingStatus(clean)) return "pending";
  if (clean === "APPROVED") return "approved";
  return "rejected";
}

function renderProfile() {
  refs.profileName.value = state.employerName || "";
  refs.profileEmail.value = state.employerEmail || "";
  refs.profileOrganization.value = state.organizationName || "";
  if (!refs.profileDescription.dataset.userEdited) {
    refs.profileDescription.value = state.profileDescription || "";
  }
  if (!refs.jobOrganizationName.value || !state.currentJobId) {
    refs.jobOrganizationName.value = state.organizationName || "";
  }
}

function jobSummaryMarkup(job) {
  const status = jobState(job);
  const applicants = Number(job.applicantsCount ?? 0);
  const totalSlots = Number(job.totalSlots ?? job.volunteersNeeded ?? 0);
  const toggleLabel = status.label === "Open" ? "Close" : "Reopen";
  const isToggling = state.togglingJobIds.has(job.id);

  return `
    <div class="employer-job-item">
      <div class="employer-job-heading">
        <h3>${escapeHtml(job.title || "Untitled Opportunity")}</h3>
        <span class="employer-state ${escapeHtml(status.tone)}">${escapeHtml(status.label)}</span>
      </div>
      <div>${escapeHtml(job.jobTitle || job.organizationName || "Volunteer opportunity")}</div>
      <div class="employer-mini-meta">
        <span class="employer-pill">${escapeHtml(formatDateTime(job.applicationDeadline || job.eventDateTime || job.postedDate))}</span>
        <span class="employer-pill">Location: ${escapeHtml(job.locationName || job.locationString || "TBD")}</span>
        <span class="employer-pill">Applicants: ${escapeHtml(applicants)}</span>
        <span class="employer-pill">Slots: ${escapeHtml(totalSlots)}</span>
      </div>
      <div class="employer-button-row" style="margin-top: 12px;">
        <button class="employer-btn secondary" type="button" data-action="edit" data-job-id="${escapeHtml(job.id)}">Edit</button>
        <button class="employer-btn ghost" type="button" data-action="filter-applicants" data-job-id="${escapeHtml(job.id)}">Applicants</button>
        <button class="employer-btn ghost" type="button" data-action="toggle-status" data-job-id="${escapeHtml(job.id)}" ${isToggling ? "disabled" : ""}>${escapeHtml(toggleLabel)}</button>
        <button class="employer-btn danger" type="button" data-action="delete" data-job-id="${escapeHtml(job.id)}">Delete</button>
      </div>
    </div>
  `;
}

function renderJobs() {
  if (state.loadingJobs) {
    refs.jobsList.innerHTML = '<div class="employer-empty">Loading opportunities...</div>';
    return;
  }
  if (!state.employerUid) {
    refs.jobsList.innerHTML = '<div class="employer-empty">Sign in to load your opportunities.</div>';
    return;
  }
  if (state.currentJobs.length === 0) {
    refs.jobsList.innerHTML = '<div class="employer-empty">No opportunities posted yet. Use the editor to publish your first listing.</div>';
    renderOverview();
    return;
  }
  refs.jobsList.innerHTML = state.currentJobs.map(jobSummaryMarkup).join("");
  renderOverview();
}

function renderApplicationFilters() {
  if (!state.employerUid || state.currentApplications.length === 0) {
    refs.applicationFilters.classList.add("employer-hidden");
    refs.applicationJobFilter.innerHTML = '<option value="">All Opportunities</option>';
    return;
  }

  const options = state.currentJobs
    .map((job) => `<option value="${escapeHtml(job.id)}">${escapeHtml(job.title || "Opportunity")}</option>`)
    .join("");
  const current = refs.applicationJobFilter.value;
  refs.applicationJobFilter.innerHTML = `<option value="">All Opportunities</option>${options}`;
  refs.applicationJobFilter.value = state.currentJobs.some((job) => job.id === current) ? current : "";
  refs.applicationFilters.classList.remove("employer-hidden");
}

function applicationMarkup(item) {
  const pending = isPendingStatus(item.status);
  const updating = state.updatingApplicationIds.has(item.id);
  const statusLabel = applicationStatusLabel(item.status);
  const resumeLink = item.resumeDownloadUrl
    ? `<a href="${escapeHtml(item.resumeDownloadUrl)}" target="_blank" rel="noreferrer">Resume: ${escapeHtml(item.resumeFileName || "Open PDF")}</a>`
    : "";

  return `
    <div class="employer-application-item">
      <div class="employer-job-heading">
        <h3>${escapeHtml(item.volunteerName)}</h3>
        <span class="employer-state ${escapeHtml(applicationStateTone(item.status))}">${escapeHtml(statusLabel)}</span>
      </div>
      <div>${escapeHtml(item.jobTitle)}</div>
      <div class="employer-meta">
        ${item.volunteerEmail ? `<span class="employer-pill">${escapeHtml(item.volunteerEmail)}</span>` : ""}
        <span class="employer-pill">Applied: ${escapeHtml(formatDateTime(item.appliedAt))}</span>
      </div>
      ${resumeLink ? `<div style="margin-top: 12px;">${resumeLink}</div>` : ""}
      ${
        pending
          ? `<div class="employer-button-row" style="margin-top: 12px;">
              <button class="employer-btn primary" type="button" data-action="approve" data-application-id="${escapeHtml(item.id)}" ${updating ? "disabled" : ""}>Approve</button>
              <button class="employer-btn secondary" type="button" data-action="reject" data-application-id="${escapeHtml(item.id)}" ${updating ? "disabled" : ""}>Reject</button>
            </div>`
          : ""
      }
    </div>
  `;
}

function renderApplications() {
  renderApplicationFilters();
  if (state.loadingApplications) {
    refs.applicationsList.innerHTML = '<div class="employer-empty">Loading applications...</div>';
    return;
  }
  if (!state.employerUid) {
    refs.applicationsList.innerHTML = '<div class="employer-empty">Sign in to review applications.</div>';
    return;
  }
  const filterJobId = refs.applicationJobFilter.value;
  const items = filterJobId
    ? state.currentApplications.filter((item) => item.jobId === filterJobId)
    : state.currentApplications;
  if (items.length === 0) {
    refs.applicationsList.innerHTML = '<div class="employer-empty">No applications found for the current filter.</div>';
    renderOverview();
    return;
  }
  refs.applicationsList.innerHTML = items.map(applicationMarkup).join("");
  renderOverview();
}

function updateJobDescriptionCount() {
  if (!refs.jobDescriptionCount) return;
  const count = refs.jobDescription?.value.length ?? 0;
  refs.jobDescriptionCount.textContent = `${count.toLocaleString()} / 2,000`;
}

function resetJobComposer() {
  state.currentJobId = "";
  refs.composerTitle.textContent = "Post Opportunity";
  refs.saveJobButton.textContent = "Post Opportunity";
  refs.cancelEditButton.classList.add("employer-hidden");
  refs.jobForm.reset();
  refs.jobCategory.value = "Community";
  refs.jobVolunteersNeeded.value = "1";
  refs.jobOrganizationName.value = state.organizationName || "";
  refs.jobScheduledDate.value = toDatetimeLocalValue(new Date(Date.now() + (24 * 60 * 60 * 1000)));
  updateJobDescriptionCount();
}

function loadJobIntoComposer(jobId) {
  const job = state.currentJobs.find((entry) => entry.id === jobId);
  if (!job) return;
  state.currentJobId = jobId;
  refs.composerTitle.textContent = "Edit Opportunity";
  refs.saveJobButton.textContent = "Save Changes";
  refs.cancelEditButton.classList.remove("employer-hidden");
  refs.jobOrganizationName.value = job.organizationName || state.organizationName || "";
  refs.jobCategory.value = job.category || "Community";
  refs.jobOpportunityTitle.value = job.title || "";
  refs.jobRoleTitle.value = job.jobTitle || "";
  refs.jobLocation.value = job.locationName || job.locationString || "";
  refs.jobVolunteersNeeded.value = String(job.volunteersNeeded ?? job.totalSlots ?? 1);
  refs.jobScheduledDate.value = toDatetimeLocalValue(resolveScheduledDate(job));
  refs.jobDescription.value = job.description || "";
  updateJobDescriptionCount();
  window.scrollTo({top: 0, behavior: "smooth"});
}

async function refreshPortalData() {
  if (!state.employerUid) return false;
  state.loadingJobs = true;
  state.loadingApplications = true;
  renderJobs();
  renderApplications();
  try {
    const jobs = await fetchPostedJobs(state.employerUid);
    state.currentJobs = jobs;
    state.loadingJobs = false;
    renderJobs();
    const applications = await fetchManagedApplications(state.employerUid, jobs);
    state.currentApplications = applications;
    state.loadingApplications = false;
    renderApplications();
    return true;
  } catch (error) {
    state.loadingJobs = false;
    state.loadingApplications = false;
    renderJobs();
    renderApplications();
    showStatus(error.message || "Unable to load employer data.", "error");
    return false;
  }
}

async function saveEmployerProfile() {
  const name = normalizeText(refs.profileName.value);
  const contactEmail = normalizeText(refs.profileEmail.value);
  const organizationName = normalizeText(refs.profileOrganization.value);
  const description = normalizeText(refs.profileDescription.value);

  if (!name || !contactEmail || !organizationName) {
    throw new Error("Display name, contact email, and organization name are required.");
  }

  await setDoc(doc(db, "users", state.employerUid), {
    name,
    username: name,
    organizationName,
    updatedAt: serverTimestamp(),
  }, {merge: true});

  await setDoc(doc(db, "employers", state.employerUid), {
    uid: state.employerUid,
    organizationName,
    contactEmail,
    description,
    profileCompleted: true,
    updatedAt: serverTimestamp(),
    lastUpdatedAt: serverTimestamp(),
  }, {merge: true});

  state.employerName = name;
  state.employerEmail = contactEmail;
  state.organizationName = organizationName;
  state.profileDescription = description;
  renderSessionChip();
  if (!state.currentJobId) {
    refs.jobOrganizationName.value = organizationName;
  }
}

function formatDateString(date) {
  return new Intl.DateTimeFormat("en-US", {
    month: "2-digit",
    day: "2-digit",
    year: "numeric",
  }).format(date);
}

function formatTimeString(date) {
  return new Intl.DateTimeFormat("en-US", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
  }).format(date);
}

async function saveJobPosting() {
  const organizationName = normalizeText(refs.jobOrganizationName.value);
  const opportunityTitle = normalizeText(refs.jobOpportunityTitle.value);
  const roleTitle = normalizeText(refs.jobRoleTitle.value);
  const description = normalizeText(refs.jobDescription.value);
  const location = normalizeText(refs.jobLocation.value);
  const category = normalizeText(refs.jobCategory.value) || "Community";
  const volunteersNeeded = Number(refs.jobVolunteersNeeded.value);
  const scheduledDate = parseDateTimeLocal(refs.jobScheduledDate.value);

  if (!opportunityTitle || !description || !location || !scheduledDate) {
    throw new Error("Opportunity title, description, location, and date are required.");
  }
  if (!organizationName) {
    throw new Error("Organization name is required.");
  }
  if (!roleTitle) {
    throw new Error("Role / job title is required.");
  }
  if (!Number.isFinite(volunteersNeeded) || volunteersNeeded <= 0) {
    throw new Error("Volunteers needed must be at least 1.");
  }

  const jobRef = state.currentJobId
    ? doc(db, "jobs", state.currentJobId)
    : doc(collection(db, "jobs"));
  const existingJob = state.currentJobs.find((item) => item.id === state.currentJobId) || {};
  const eventTimestamp = Timestamp.fromDate(scheduledDate);

  const payload = {
    postingId: jobRef.id,
    title: opportunityTitle,
    eventName: opportunityTitle,
    organizationName,
    jobTitle: roleTitle,
    description,
    locationString: location,
    locationName: location,
    location,
    category,
    jobType: "Volunteer",
    salaryOrCompensation: "",
    date: formatDateString(scheduledDate),
    time: formatTimeString(scheduledDate),
    applicationDeadline: eventTimestamp,
    eventDateTime: eventTimestamp,
    eventTimestamp,
    volunteersNeeded: Math.max(1, Math.trunc(volunteersNeeded)),
    totalSlots: Math.max(1, Math.trunc(volunteersNeeded)),
    slotsFilled: Number(existingJob.slotsFilled ?? 0),
    status: normalizeText(existingJob.status).toLowerCase() === "closed" ? "closed" : "open",
    applicantsCount: Number(existingJob.applicantsCount ?? 0),
    employerUid: state.employerUid,
    employerId: state.employerUid,
    employerName: state.employerName || organizationName,
    createdBy: state.employerUid,
    isActive: normalizeText(existingJob.status).toLowerCase() === "closed" ? false : true,
    timestamp: serverTimestamp(),
    lastUpdatedAt: serverTimestamp(),
  };

  if (!state.currentJobId) {
    payload.postedDate = serverTimestamp();
    payload.createdAt = serverTimestamp();
  }

  await setDoc(jobRef, payload, {merge: true});
  state.organizationName = organizationName;
}

async function deleteJobPosting(jobId) {
  const confirmed = window.confirm("Delete this opportunity? This removes it from your employer list.");
  if (!confirmed) return;

  try {
    await deleteDoc(doc(db, "jobs", jobId));
  } catch (error) {
    await setDoc(doc(db, "jobs", jobId), {
      isDeleted: true,
      status: "closed",
      isActive: false,
      lastUpdatedAt: serverTimestamp(),
    }, {merge: true});
  }

  if (state.currentJobId === jobId) {
    resetJobComposer();
  }
  showStatus("Opportunity removed.", "info");
  await refreshPortalData();
}

async function toggleJobStatus(jobId) {
  const job = state.currentJobs.find((entry) => entry.id === jobId);
  if (!job) return;
  const nextStatus = normalizeText(job.status).toLowerCase() === "closed" ? "open" : "closed";
  state.togglingJobIds.add(jobId);
  renderJobs();
  try {
    await setDoc(doc(db, "jobs", jobId), {
      status: nextStatus,
      isActive: nextStatus === "open",
      lastUpdatedAt: serverTimestamp(),
    }, {merge: true});
    showStatus(`Opportunity marked as ${nextStatus}.`, "info");
    await refreshPortalData();
  } catch (error) {
    showStatus(error.message || "Unable to update opportunity status.", "error");
  } finally {
    state.togglingJobIds.delete(jobId);
    renderJobs();
  }
}

async function updateApplicationStatus(applicationId, status) {
  const application = state.currentApplications.find((item) => item.id === applicationId);
  if (!application) return;
  if (!isPendingStatus(application.status)) {
    showStatus("Only pending applications can be updated.", "error");
    return;
  }

  state.updatingApplicationIds.add(applicationId);
  renderApplications();
  try {
    const patch = {
      status,
      lastUpdatedAt: serverTimestamp(),
    };
    let didWrite = false;

    const candidateRootIds = new Set(
      [application.rootApplicationId, application.applicationId, application.documentId].filter(Boolean)
    );
    for (const rootId of candidateRootIds) {
      const rootRef = doc(db, "applications", rootId);
      const snap = await getDoc(rootRef).catch(() => null);
      if (snap?.exists()) {
        await setDoc(rootRef, patch, {merge: true});
        didWrite = true;
      }
    }

    const candidateSubIds = new Set(
      [application.subDocumentId, application.documentId, application.applicationId, application.volunteerId].filter(Boolean)
    );
    for (const subId of candidateSubIds) {
      const subRef = doc(db, "jobs", application.jobId, "applications", subId);
      const snap = await getDoc(subRef).catch(() => null);
      if (snap?.exists()) {
        await setDoc(subRef, patch, {merge: true});
        didWrite = true;
      }
    }

    if (!didWrite) {
      throw new Error("Application record could not be located for update.");
    }

    showStatus(`Application marked as ${applicationStatusLabel(status)}.`, "info");
    await refreshPortalData();
  } catch (error) {
    showStatus(error.message || "Unable to update application status.", "error");
  } finally {
    state.updatingApplicationIds.delete(applicationId);
    renderApplications();
  }
}

function attachEventDelegates() {
  refs.jobsList.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const action = button.dataset.action;
    const jobId = normalizeText(button.dataset.jobId);
    if (!jobId) return;

    if (action === "edit") {
      loadJobIntoComposer(jobId);
    } else if (action === "filter-applicants") {
      refs.applicationJobFilter.value = jobId;
      renderApplications();
      document.querySelector("#applications-list")?.scrollIntoView({behavior: "smooth", block: "start"});
    } else if (action === "toggle-status") {
      toggleJobStatus(jobId);
    } else if (action === "delete") {
      deleteJobPosting(jobId).catch((error) => {
        showStatus(error.message || "Unable to delete opportunity.", "error");
      });
    }
  });

  refs.applicationsList.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const action = button.dataset.action;
    const applicationId = normalizeText(button.dataset.applicationId);
    if (!applicationId) return;

    if (action === "approve") {
      updateApplicationStatus(applicationId, "APPROVED");
    } else if (action === "reject") {
      updateApplicationStatus(applicationId, "REJECTED");
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
  if (!state.employerUid) return;
  refs.refreshPortalButton.disabled = true;
  showStatus("Refreshing your employer data...", "info");
  try {
    const didRefresh = await refreshPortalData();
    if (didRefresh) {
      showStatus("Employer data is up to date.", "info");
    }
  } finally {
    refs.refreshPortalButton.disabled = false;
  }
});

refs.profileDescription?.addEventListener("input", () => {
  refs.profileDescription.dataset.userEdited = "true";
});

refs.jobDescription?.addEventListener("input", updateJobDescriptionCount);

refs.profileForm?.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!state.employerUid) {
    showStatus("Please sign in first.", "error");
    return;
  }
  state.savingProfile = true;
  refs.saveProfileButton.disabled = true;
  showStatus("Saving employer profile...", "info");
  try {
    await saveEmployerProfile();
    showStatus("Employer profile saved.", "info");
  } catch (error) {
    showStatus(error.message || "Unable to save employer profile.", "error");
  } finally {
    state.savingProfile = false;
    refs.saveProfileButton.disabled = false;
  }
});

refs.jobForm?.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!state.employerUid) {
    showStatus("Please sign in first.", "error");
    return;
  }
  const isEditMode = Boolean(state.currentJobId);
  state.savingJob = true;
  refs.saveJobButton.disabled = true;
  showStatus(isEditMode ? "Saving opportunity changes..." : "Posting opportunity...", "info");
  try {
    await saveJobPosting();
    showStatus(isEditMode ? "Opportunity updated." : "Opportunity posted.", "info");
    resetJobComposer();
    await refreshPortalData();
  } catch (error) {
    showStatus(error.message || "Unable to save opportunity.", "error");
  } finally {
    state.savingJob = false;
    refs.saveJobButton.disabled = false;
  }
});

refs.cancelEditButton?.addEventListener("click", () => {
  resetJobComposer();
  showStatus("Edit mode cancelled.", "info");
});

refs.applicationJobFilter?.addEventListener("change", () => {
  renderApplications();
});

attachEventDelegates();
resetJobComposer();

onAuthStateChanged(auth, async (user) => {
  state.employerUid = "";
  state.employerName = "";
  state.employerEmail = "";
  state.organizationName = "";
  state.profileDescription = "";
  state.currentJobId = "";
  state.currentJobs = [];
  state.currentApplications = [];
  refs.profileDescription.value = "";
  delete refs.profileDescription.dataset.userEdited;
  renderAuthState();
  renderJobs();
  renderApplications();
  resetJobComposer();

  if (!user) {
    return;
  }

  showStatus("Checking employer access...", "info");
  try {
    await requireAppCheckToken();
    const identity = await fetchEmployerIdentity(user.uid);
    state.employerUid = user.uid;
    state.employerName = identity.name;
    state.employerEmail = identity.email;
    state.organizationName = identity.organizationName;
    state.profileDescription = identity.description;
    renderAuthState();
    renderProfile();
    showStatus("Employer access granted.", "info");
    await refreshPortalData();
  } catch (error) {
    await signOut(auth).catch(() => {});
    showStatus(error.message || "Employer access is not available for this account.", "error");
  }
});
