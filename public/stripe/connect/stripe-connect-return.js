(function () {
  function readConfig() {
    var el = document.getElementById("stripe-connect-return-config");
    if (!el) return {};
    try {
      return JSON.parse(el.textContent || "{}");
    } catch (error) {
      return {};
    }
  }

  function resolveConfig(config) {
    var params = new URLSearchParams(window.location.search || "");
    return {
      result: params.get("result") || config.result || "return",
      flow: params.get("flow") || config.flow || "connect_onboarding",
      returnToApp: params.get("return_to_app") === "1"
    };
  }

  function buildAppUrl(config) {
    var result = config.result;
    var flow = config.flow;
    return (
      "volunteersapp://stripe-connect?flow=" +
      encodeURIComponent(flow) +
      "&result=" +
      encodeURIComponent(result)
    );
  }

  function openCustomScheme(appUrl) {
    window.location.replace(appUrl);
  }

  function wirePage() {
    var config = resolveConfig(readConfig());
    var appUrl = buildAppUrl(config);
    var button = document.getElementById("open-app");
    var status = document.getElementById("status");

    if (button) {
      button.setAttribute("href", appUrl);
      button.addEventListener("click", function (event) {
        event.preventDefault();
        openCustomScheme(appUrl);
        if (status) {
          status.textContent =
            "If the app does not open, return to Volunteers App manually and refresh Payment Methods.";
        }
      });
    }

    if (config.returnToApp) {
      if (status) {
        status.textContent = "Returning to Volunteers App and refreshing your payout method…";
      }
      // ASWebAuthenticationSession captures this registered app callback and closes the
      // Stripe sheet. A visible button remains as a fallback for external browsers.
      window.setTimeout(function () {
        openCustomScheme(appUrl);
      }, 120);
    } else if (status) {
      status.textContent =
        "Tap Open Volunteers App below to refresh your payout method.";
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", wirePage);
  } else {
    wirePage();
  }
})();
