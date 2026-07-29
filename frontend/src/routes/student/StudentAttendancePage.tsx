import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { AttendanceCalendar } from "../../shared/components/AttendanceCalendar";
import { getMyAttendances } from "./api";

const NOW = new Date();

/** S-6. 본인 출석 현황. 학부모 화면(P-2)과 같은 데이터 형식을 쓴다. */
export default function StudentAttendancePage() {
  const [year, setYear] = useState(NOW.getFullYear());
  const [month, setMonth] = useState(NOW.getMonth() + 1);

  const { data, isPending } = useQuery({
    queryKey: ["student", "attendances", year, month],
    queryFn: () => getMyAttendances(year, month),
  });

  function shift(delta: number) {
    const next = new Date(year, month - 1 + delta, 1);
    setYear(next.getFullYear());
    setMonth(next.getMonth() + 1);
  }

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-slate-900">출석 현황</h2>
      {isPending || !data ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : (
        <AttendanceCalendar data={data} onPrev={() => shift(-1)} onNext={() => shift(1)} />
      )}
      <p className="text-xs text-slate-500">
        회색 "미확인"은 선생님이 아직 출석을 확정하지 않은 날입니다. 결석이 아닙니다.
      </p>
    </div>
  );
}
