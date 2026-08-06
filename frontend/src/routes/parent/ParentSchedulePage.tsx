import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSelectedChild } from "../../shared/auth/SelectedChildContext";
import { AttendanceCalendar } from "../../shared/components/AttendanceCalendar";
import { DAY_STATUS_STYLE } from "../../shared/attendance/types";
import { formatClinicSlot } from "../../shared/clinic/types";
import { getChildAttendances, getChildClinics } from "./api";

const NOW = new Date();

/**
 * P-2. 수업 출석과 클리닉을 한 화면에서 본다. <b>조회만</b>이다 —
 * 신청·취소·변경 버튼을 두지 마라.
 */
export default function ParentSchedulePage() {
  const { children, selectedStudentId, setSelectedStudentId } = useSelectedChild();
  const [year, setYear] = useState(NOW.getFullYear());
  const [month, setMonth] = useState(NOW.getMonth() + 1);

  const from = `${year}-${String(month).padStart(2, "0")}-01`;
  const to = `${year}-${String(month).padStart(2, "0")}-${String(
    new Date(year, month, 0).getDate(),
  ).padStart(2, "0")}`;

  const attendances = useQuery({
    queryKey: ["parent", "attendances", selectedStudentId, year, month],
    queryFn: () => getChildAttendances(selectedStudentId!, year, month),
    enabled: selectedStudentId !== null,
  });

  const clinics = useQuery({
    queryKey: ["parent", "clinics", selectedStudentId, from, to],
    queryFn: () => getChildClinics(selectedStudentId!, from, to),
    enabled: selectedStudentId !== null,
  });

  // 캘린더는 수업과 같은 칩으로 그린다. attendStatus가 null이면 아직 확정 전이라 PENDING이다
  const clinicEntries = useMemo(
    () =>
      (clinics.data ?? []).map((clinic) => ({
        date: clinic.clinicDate,
        status: clinic.attendStatus ?? ("PENDING" as const),
      })),
    [clinics.data],
  );

  function shift(delta: number) {
    const next = new Date(year, month - 1 + delta, 1);
    setYear(next.getFullYear());
    setMonth(next.getMonth() + 1);
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-slate-900">수업 · 클리닉 일정</h2>
        {children.length > 1 && (
          <select
            value={selectedStudentId ?? ""}
            onChange={(e) => setSelectedStudentId(Number(e.target.value))}
            className="rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm"
          >
            {children.map((child) => (
              <option key={child.studentId} value={child.studentId}>
                {child.name}
              </option>
            ))}
          </select>
        )}
      </div>

      {attendances.isPending || !attendances.data ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : (
        <AttendanceCalendar
          data={attendances.data}
          onPrev={() => shift(-1)}
          onNext={() => shift(1)}
          clinics={clinicEntries}
        />
      )}

      <section>
        <h3 className="text-sm font-semibold text-slate-700">클리닉</h3>
        {clinics.data && clinics.data.length > 0 ? (
          <ul className="mt-2 space-y-2">
            {clinics.data.map((clinic) => (
              <li
                key={clinic.clinicId}
                className="flex items-center justify-between gap-2 rounded-xl bg-white p-3
                           shadow-sm"
              >
                <div>
                  <p className="text-sm font-medium text-slate-900">
                    {formatClinicSlot(clinic)}
                  </p>
                  {clinic.changeRequestStatus === "PENDING" && (
                    <p className="text-xs text-amber-700">시간 변경을 요청해 둔 상태입니다.</p>
                  )}
                </div>
                {/* attendStatus가 null이면 아직 출석 확정 전이다 */}
                <span
                  className={`rounded-md px-2 py-1 text-xs ${
                    clinic.attendStatus
                      ? DAY_STATUS_STYLE[clinic.attendStatus].cell
                      : DAY_STATUS_STYLE.PENDING.cell
                  }`}
                >
                  {clinic.attendStatus
                    ? DAY_STATUS_STYLE[clinic.attendStatus].label
                    : "미확인"}
                </span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-2 rounded-xl bg-white p-4 text-sm text-slate-500 shadow-sm">
            이 달에 예정된 클리닉이 없습니다.
          </p>
        )}
      </section>
    </div>
  );
}
