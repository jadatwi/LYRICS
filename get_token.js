// One-time helper: gets your Spotify refresh token.
// Usage: node get_token.js <CLIENT_ID> <CLIENT_SECRET>
// Spotify app redirect URI must be exactly: http://127.0.0.1:8888/callback
const http = require("http");
const https = require("https");
const [clientId, clientSecret] = process.argv.slice(2);
if (!clientId || !clientSecret) { console.log("Usage: node get_token.js <CLIENT_ID> <CLIENT_SECRET>"); process.exit(1); }

const redirect = "http://127.0.0.1:8888/callback";
const scope = "user-read-currently-playing user-read-playback-state";
const authUrl = "https://accounts.spotify.com/authorize?response_type=code&client_id=" + clientId +
    "&scope=" + encodeURIComponent(scope) + "&redirect_uri=" + encodeURIComponent(redirect);

http.createServer((req, res) => {
    const u = new URL(req.url, "http://127.0.0.1:8888");
    if (u.pathname !== "/callback") { res.end("ok"); return; }
    const code = u.searchParams.get("code");
    const body = new URLSearchParams({ grant_type: "authorization_code", code, redirect_uri: redirect }).toString();
    const r = https.request("https://accounts.spotify.com/api/token", {
        method: "POST",
        headers: {
            "Authorization": "Basic " + Buffer.from(clientId + ":" + clientSecret).toString("base64"),
            "Content-Type": "application/x-www-form-urlencoded"
        }
    }, (resp) => {
        let d = ""; resp.on("data", c => d += c);
        resp.on("end", () => {
            const j = JSON.parse(d);
            res.end(j.refresh_token ? "Done! You can close this tab." : "Error: " + d);
            console.log(j.refresh_token ? "\nYour refresh token:\n\n" + j.refresh_token + "\n" : d);
            process.exit(0);
        });
    });
    r.write(body); r.end();
}).listen(8888, () => console.log("Open this URL in your browser and approve:\n\n" + authUrl + "\n"));
