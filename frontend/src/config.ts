/** Cấu hình build-time (Vite). Không có secret nào ở frontend: Client ID & scope là thông tin công khai. */
const env = import.meta.env;
const isMockMode = env.MODE === "mock";

export const config = {
  apiBaseUrl: (env.VITE_API_BASE_URL || (isMockMode ? "http://127.0.0.1:8080" : "")).replace(/\/$/, ""),
  authMode: (env.VITE_AUTH_MODE || (isMockMode ? "mock" : "msal")) as "msal" | "mock",
  entra: {
    clientId: env.VITE_ENTRA_CLIENT_ID || "",
    authority: env.VITE_ENTRA_AUTHORITY || "",
    apiScope: env.VITE_API_SCOPE || "",
  },
};

if (!config.apiBaseUrl) {
  console.warn("VITE_API_BASE_URL chưa được cấu hình");
}
