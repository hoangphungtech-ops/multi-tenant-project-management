import { useCallback, useEffect, useState } from "react";
import { describeError } from "../api/client";

/** Tải dữ liệu bất đồng bộ, bỏ kết quả cũ khi deps đổi (ví dụ chuyển Org). */
export function useLoad<T>(loader: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(loader, deps);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    run()
      .then((d) => { if (alive) { setData(d); setError(undefined); } })
      .catch((e) => { if (alive) setError(describeError(e)); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [run, tick]);

  return { data, setData, error, loading, reload: () => setTick((n) => n + 1) };
}
