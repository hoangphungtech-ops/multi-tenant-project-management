import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth/AuthProvider";
import { Layout } from "./components/Layout";
import { OrgProvider } from "./org/OrgProvider";
import { AuditPage } from "./pages/AuditPage";
import { BoardPage } from "./pages/BoardPage";
import { ExportsPage } from "./pages/ExportsPage";
import { LoginPage } from "./pages/LoginPage";
import { MembersPage } from "./pages/MembersPage";
import { ProjectsPage } from "./pages/ProjectsPage";

export function App() {
  const { status } = useAuth();
  if (status === "loading") return <p className="muted center">Đang khởi tạo đăng nhập…</p>;
  if (status !== "signedIn") return <LoginPage />;

  return (
    <OrgProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/projects" element={<ProjectsPage />} />
            <Route path="/projects/:projectId" element={<BoardPage />} />
            <Route path="/members" element={<MembersPage />} />
            <Route path="/audit" element={<AuditPage />} />
            <Route path="/exports" element={<ExportsPage />} />
            <Route path="*" element={<Navigate to="/projects" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </OrgProvider>
  );
}
