/* eslint-disable max-len */ // Disable max-len for this file if lines exceed 80
// Import necessary modules from Firebase SDKs
import * as admin from "firebase-admin";
// Import database, EventContext, and DataSnapshot from v1
import {DataSnapshot} from "firebase-admin/database";
import {database, EventContext} from "firebase-functions/v1";

admin.initializeApp();

interface JobPostingData {
  employerUid: string;
  organizationName?: string;
  eventName?: string;
  jobTitle?: string;
  description?: string;
  date?: string;
  time?: string;
  location?: string;
  category?: string;
  volunteersNeeded?: number;
  status?: string;
  timestamp?: number;
}

// Use the explicitly imported 'database' object
export const onNewJobPosting = database.ref("/job_postings/{postingId}")
  .onCreate(
    async (snapshot: DataSnapshot, context: EventContext) => {
      const jobData = snapshot.val() as JobPostingData | null;
      const postingId = context.params.postingId;

      if (!jobData?.employerUid) {
        console.error(
          `New job posting at ID '${postingId}' is missing data` +
          " or employerUid. Job Data:", jobData
        );
        return {success: false, error: "Missing employerUid"};
      }

      const employerUid: string = jobData.employerUid;

      console.log(
        `Processing new job posting. ID: '${postingId}',` +
        ` Employer UID: '${employerUid}'`
      );

      const employerJobRef = admin.database().ref(
        `/employer_job_postings/${employerUid}/${postingId}`
      );

      try {
        await employerJobRef.set(true);
        console.log(
          "Successfully created entry in /employer_job_postings/" +
          ` for employer '${employerUid}', posting ID '${postingId}'.`
        );
        return {success: true, postingId, employerUid};
      } catch (error) {
        console.error(
          "Error creating entry in /employer_job_postings/" +
          ` for employer '${employerUid}', posting ID '${postingId}':`,
          error
        );
        return {success: false, error: (error as Error).message};
      }
    }
  );

// Example of a simple HTTP function (if you had one from template)
// import {onRequest} from "firebase-functions/v2/https";
// import * as logger from "firebase-functions/logger";
//
// export const helloWorld = onRequest((request, response) => {
// logger.info("Hello logs!", {structuredData: true});
// response.send("Hello from Firebase! (TypeScript)");
// });
