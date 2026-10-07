/**
* PHP Email Form Validation - v3.10
* URL: https://bootstrapmade.com/php-email-form/
* Author: BootstrapMade.com
*/
(function () {
  "use strict";

  let forms = document.querySelectorAll('.php-email-form');

  forms.forEach( function(e) {
    e.addEventListener('submit', function(event) {
      event.preventDefault(); // Prevent default form submission and page reload

      let thisForm = this;

      let action = thisForm.getAttribute('action');
      let recaptchaSiteKey = thisForm.getAttribute('data-recaptcha-site-key'); // Renamed for clarity
      
      if( ! action ) {
        displayError(thisForm, 'The form action property is not set!');
        return;
      }

      // Show loading state, hide previous messages
      thisForm.querySelector('.loading').classList.add('d-block');
      thisForm.querySelector('.error-message').classList.remove('d-block');
      thisForm.querySelector('.sent-message').classList.remove('d-block');

      let formData = new FormData( thisForm );

      const hasTurnstileWidget = thisForm.querySelector('.cf-turnstile') !== null;
      if (hasTurnstileWidget) {
        const turnstileToken = formData.get('cf-turnstile-response');
        if (!turnstileToken) {
          displayError(thisForm, 'Please complete the CAPTCHA challenge.');
          return;
        }
      }

      // --- ReCAPTCHA Enterprise Integration ---
      // This section integrates with reCAPTCHA Enterprise.
      // Ensure the reCAPTCHA Enterprise JavaScript library is loaded on your page.
      // This is often done via: <script src="https://www.google.com/recaptcha/enterprise.js?render=YOUR_SITE_KEY"></script>
      // Or, if using Firebase App Check, the integration is handled differently.
      // The `unsafe-eval` CSP error might still be related to how this external script
      // or its internal workings are handled, especially if a strict CSP is in place.

      if ( recaptchaSiteKey ) {
        // Check if grecaptcha.enterprise is loaded and ready
        if(typeof grecaptcha !== "undefined" && typeof grecaptcha.enterprise !== "undefined" ) {
          grecaptcha.enterprise.ready(function() {
            try {
              // Execute reCAPTCHA Enterprise, passing the site key
              grecaptcha.enterprise.execute(recaptchaSiteKey, {action: 'contact_form_submit'}) // Use a specific action name
              .then(token => {
                formData.set('recaptcha-response', token);
                submitFormWithCloudFunction(thisForm, action, formData);
              })
              .catch(error => {
                displayError(thisForm, 'reCAPTCHA token generation failed: ' + error.message);
              });
            } catch(error) {
              displayError(thisForm, 'reCAPTCHA execution error: ' + error.message);
            }
          });
        } else {
          displayError(thisForm, 'The reCAPTCHA Enterprise JavaScript API is not loaded or incorrectly initialized!');
        }
      } else {
        // No reCAPTCHA, proceed directly to form submission
        submitFormWithCloudFunction(thisForm, action, formData);
      }
    });
  });

  // Function to handle the actual submission to the Cloud Function
  function submitFormWithCloudFunction(thisForm, action, formData) {
    fetch(action, {
      method: 'POST',
      body: formData
    })
    .then(response => {
      // Check if the HTTP response status is in the 2xx range (success)
      if( response.ok ) {
        return response.text(); // Get the response body as plain text
      } else {
        // If server responded with an error status (e.g., 4xx, 5xx)
        return response.text().then(errorText => {
            // Include server's message in the error for better debugging
            throw new Error(`Server responded with status ${response.status}: ${errorText || response.statusText}`);
        });
      }
    })
    .then(data => {
      // This block is executed if the fetch was successful (response.ok was true)
      thisForm.querySelector('.loading').classList.remove('d-block');
      
      // --- CRITICAL CORRECTION HERE ---
      // Your Cloud Function returns a successful string like
      // "Form submission received successfully! (More processing to come)".
      // Since `response.ok` already confirmed success, we can directly display
      // the success message. The `data` variable here will contain that string
      // from your Cloud Function, which you could also choose to display.
      thisForm.querySelector('.sent-message').classList.add('d-block');
      thisForm.reset(); // Clear the form fields after successful submission
      
      console.log("Cloud Function Response:", data); // Log the actual response for debugging
    })
    .catch((error) => {
      // This block catches any errors during the fetch operation or from the .then blocks
      // (e.g., network issues, or errors thrown from the response.ok check above)
      displayError(thisForm, error.message); // Display the specific error message
    });
  }

  // Helper function to display errors in the form's error message div
  function displayError(thisForm, errorMsg) {
    thisForm.querySelector('.loading').classList.remove('d-block');
    thisForm.querySelector('.error-message').innerHTML = errorMsg;
    thisForm.querySelector('.error-message').classList.add('d-block');
    console.error("Form submission error:", errorMsg); // Log to console for debugging
  }

})();
