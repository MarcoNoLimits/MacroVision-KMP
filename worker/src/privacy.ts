export const PRIVACY_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Privacy Policy — FitCal</title>
<meta name="description" content="How FitCal collects, uses, and protects your data.">
<meta name="robots" content="index,follow">
<style>
  :root {
    --bg: #f8fafc;
    --card: #ffffff;
    --text: #0f172a;
    --muted: #64748b;
    --border: #e2e8f0;
    --accent: #10b981;
    --danger: #b91c1c;
    --danger-bg: #fef2f2;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    padding: 48px 20px 80px;
    background: var(--bg);
    color: var(--text);
    font: 16px/1.65 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  }
  main { max-width: 760px; margin: 0 auto; }
  h1 { font-size: 2rem; margin: 0 0 8px; letter-spacing: -0.02em; }
  .sub { color: var(--muted); margin: 0 0 32px; font-size: 0.95rem; }
  section {
    background: var(--card);
    border: 1px solid var(--border);
    border-radius: 14px;
    padding: 22px 24px;
    margin-bottom: 16px;
  }
  h2 { font-size: 1.1rem; margin: 0 0 10px; }
  p, li { color: #334155; font-size: 0.95rem; }
  ul { padding-left: 20px; margin: 8px 0; }
  li { margin-bottom: 6px; }
  .callout {
    background: var(--danger-bg);
    border: 1px solid #fca5a5;
    border-radius: 14px;
    padding: 20px 24px;
    margin-bottom: 16px;
  }
  .callout h2 { color: var(--danger); }
  .callout p { color: var(--danger); }
  a { color: #059669; }
  footer { color: var(--muted); font-size: 0.85rem; margin-top: 32px; text-align: center; }
</style>
</head>
<body>
<main>

  <h1>Privacy Policy</h1>
  <p class="sub">FitCal &middot; Last updated: October 2026</p>

  <div class="callout">
    <h2>Not medical advice</h2>
    <p>FitCal provides automated nutrition <strong>estimates only</strong>. Portion sizes
    inferred from a photograph are approximate and may be significantly wrong. FitCal is
    not a medical device and does not provide diagnosis or treatment. Do not use FitCal to
    manage diabetes, eating disorders, allergies, pregnancy, or any medical condition.
    FitCal is intended for adults aged 18 and over.</p>
  </div>

  <section>
    <h2>What FitCal is</h2>
    <p>FitCal is a mobile application that estimates the nutritional content of a meal from a
    photograph you take. It also tracks water intake, weight, and logged meals. It is an
    informational tool only.</p>
  </section>

  <section>
    <h2>What we collect</h2>
    <ul>
      <li>Meal photographs you choose to capture</li>
      <li>Nutrition estimates generated from those photographs</li>
      <li>Your account email address, if you choose to create an account (the app works without one)</li>
      <li>Water intake, weight, and meal log entries you record</li>
      <li>Technical data: device identifiers, crash logs, and diagnostic events</li>
      <li>Advertising identifiers and ad interaction events (see <em>Advertising</em> below)</li>
    </ul>
  </section>

  <section>
    <h2>Why this is sensitive information</h2>
    <p>Dietary and nutrition information linked to an identifiable person can be classified as
    health data, which privacy law treats as a special category requiring your explicit
    consent. That is why FitCal asks before processing anything, never processes data before
    you accept, and gives you an equal option to decline personalized ads.</p>
  </section>

  <section>
    <h2>Legal bases for processing (GDPR)</h2>
    <ul>
      <li><strong>Consent</strong> (Art. 6(1)(a)) — meal photo analysis and personalized advertising</li>
      <li><strong>Contract</strong> (Art. 6(1)(b)) — providing the app's core functionality</li>
      <li><strong>Legitimate interests</strong> (Art. 6(1)(f)) — crash diagnostics and security, balanced against your rights</li>
    </ul>
    <p>For special-category processing we rely on your explicit consent under Art. 9(2)(a).</p>
  </section>

  <section>
    <h2>Who processes your data</h2>
    <p>Our servers run on Cloudflare. Photographs are forwarded to third-party AI providers —
    OpenRouter, Google, and Groq — solely to generate estimates. Account data is stored in
    Supabase. Payments are handled by RevenueCat and the relevant app store; we never see your
    full card details.</p>
  </section>

  <section>
    <h2>International transfers</h2>
    <p>Some providers process data outside the European Economic Area. Where that occurs,
    transfers are covered by Standard Contractual Clauses and a transfer impact assessment.</p>
  </section>

  <section>
    <h2>Advertising</h2>
    <p>FitCal is supported by advertising. We use Google AdMob and, where enabled, AppLovin MAX.</p>
    <p><strong>Personalized ads are OFF by default.</strong> If you opt in — and separately approve
    the device-level prompt — our ad partners may use your activity to show more relevant ads.
    You can turn personalized ads off at any time in Settings → Privacy without losing any core
    feature. European users are shown a consent platform before any ad request is made.</p>
  </section>

  <section>
    <h2>How long we keep data</h2>
    <p>Meal photographs and nutrition logs are retained until you delete them or close your
    account. Cached analysis results are retained for up to 30 days. Crash and diagnostic logs
    are retained for up to 90 days.</p>
  </section>

  <section>
    <h2>Your rights</h2>
    <ul>
      <li>Withdraw consent for personalized ads — Settings → Privacy</li>
      <li>Delete your account and all associated data — Settings → Privacy</li>
      <li>Request a copy of the data we hold about you</li>
      <li>Correct inaccurate information</li>
      <li>Object to processing, and restrict it</li>
      <li>Lodge a complaint with your supervisory authority</li>
    </ul>
  </section>

  <section>
    <h2>Children</h2>
    <p>FitCal is not directed at children under 18, or under the minimum age of digital consent
    in your jurisdiction. We do not knowingly collect data from children. If you believe a child
    has provided us data, contact us and we will delete it.</p>
  </section>

  <section>
    <h2>Security</h2>
    <p>Data is encrypted in transit using TLS. Access is authenticated with short-lived tokens. Ad
    SDKs are initialized only after consent is resolved, and cloud backups of app data are
    disabled on Android.</p>
  </section>

  <section>
    <h2>Your choices</h2>
    <p>Declining personalized ads leaves every core feature of FitCal fully available. You may use
    the app as a guest without creating an account.</p>
  </section>

  <footer>
    <p>Questions about this policy? Contact <a href="mailto:privacy@fitcal.app">privacy@fitcal.app</a>.</p>
  </footer>

</main>
</body>
</html>`;
