package com.example.volunteersApp.models;

import androidx.annotation.Keep; // For ProGuard/R8
// import com.google.firebase.firestore.IgnoreExtraProperties; // Add if you use it
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;
import java.util.Objects; // For equals() and hashCode()

@Keep // Keep the class and its members for ProGuard/R8
@SuppressWarnings("unused") // Suppress "unused" warnings for methods used by Firestore via reflection
// @IgnoreExtraProperties // Uncomment if you want to ignore extra fields from Firestore
public class UserReport {
    private String reportedUserName;
    private String reportedUserEmail; // Optional, if provided
    private String eventName;
    private String reasonForReport;
    private String reportingUserDisplayName;
    private String reportingUserId;

    @ServerTimestamp // Automatically set by Firestore
    private Date timestamp;

    // Firestore requires a public no-argument constructor
    public UserReport() {}

    public UserReport(String reportedUserName, String reportedUserEmail, String eventName,
                      String reasonForReport, String reportingUserDisplayName, String reportingUserId) {
        this.reportedUserName = reportedUserName;
        this.reportedUserEmail = reportedUserEmail;
        this.eventName = eventName;
        this.reasonForReport = reasonForReport;
        this.reportingUserDisplayName = reportingUserDisplayName;
        this.reportingUserId = reportingUserId;
        // Timestamp will be set by Firestore server-side
    }

    // --- Getters (Required by Firestore if fields are private) ---
    public String getReportedUserName() { return reportedUserName; }
    public String getReportedUserEmail() { return reportedUserEmail; }

    // Renamed from getTitle() to getEventName() to match field name for clarity
    // If your Firestore document has a field "eventName", this is fine.
    // If your Firestore document has a field "title", you'd use @PropertyName("title") on eventName field
    // or keep getTitle() and add @PropertyName("eventName") to the getter.
    // For simplicity, aligning getter with field name:
    public String getEventName() { return eventName; }

    public String getReasonForReport() { return reasonForReport; }
    public String getReportingUserDisplayName() { return reportingUserDisplayName; }
    public String getReportingUserId() { return reportingUserId; }
    public Date getTimestamp() { return timestamp; }

    // --- Setters (Often required by Firestore for deserialization if fields are private) ---
    public void setReportedUserName(String reportedUserName) { this.reportedUserName = reportedUserName; }
    public void setReportedUserEmail(String reportedUserEmail) { this.reportedUserEmail = reportedUserEmail; }
    public void setEventName(String eventName) { this.eventName = eventName; } // Matched to getEventName()
    public void setReasonForReport(String reasonForReport) { this.reasonForReport = reasonForReport; }
    public void setReportingUserDisplayName(String reportingUserDisplayName) { this.reportingUserDisplayName = reportingUserDisplayName; }
    public void setReportingUserId(String reportingUserId) { this.reportingUserId = reportingUserId; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    // It's good practice to implement equals() and hashCode() for data classes
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserReport that = (UserReport) o;
        return Objects.equals(reportedUserName, that.reportedUserName) &&
                Objects.equals(reportedUserEmail, that.reportedUserEmail) &&
                Objects.equals(eventName, that.eventName) &&
                Objects.equals(reasonForReport, that.reasonForReport) &&
                Objects.equals(reportingUserDisplayName, that.reportingUserDisplayName) &&
                Objects.equals(reportingUserId, that.reportingUserId) &&
                Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reportedUserName, reportedUserEmail, eventName, reasonForReport,
                reportingUserDisplayName, reportingUserId, timestamp);
    }

    @Override
    public String toString() {
        return "UserReport{" +
                "reportedUserName='" + reportedUserName + '\'' +
                ", eventName='" + eventName + '\'' +
                ", reasonForReport='" + reasonForReport + '\'' +
                ", reportingUserId='" + reportingUserId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
