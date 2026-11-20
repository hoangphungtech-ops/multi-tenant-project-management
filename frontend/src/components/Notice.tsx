export function ErrorNotice({ message, onClose }: { message?: string; onClose?: () => void }) {
  if (!message) return null;
  return (
    <div className="notice notice-error" role="alert">
      <span>{message}</span>
      {onClose && <button className="link" onClick={onClose} aria-label="Đóng">✕</button>}
    </div>
  );
}

export function RoleBadge({ role }: { role: string }) {
  return <span className={`badge badge-${role.toLowerCase()}`}>{role}</span>;
}

export function GuestHint() {
  return (
    <p className="muted small">
      Bạn đang ở vai trò <b>Guest</b> (chỉ xem). Mọi thao tác ghi đều bị API từ chối 403.
    </p>
  );
}

export function fmtDate(s?: string | null) {
  return s ? new Date(s).toLocaleString("vi-VN") : "—";
}
