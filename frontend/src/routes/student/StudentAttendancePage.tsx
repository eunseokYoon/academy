import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { AttendanceCalendar } from "../../shared/components/AttendanceCalendar";
import { DAY_STATUS_STYLE } from "../../shared/attendance/types";
import { DAY_LABELS } from "../teacher/format";
import { getMyAttendances, listMyClinics } from "./api";

const NOW = new Date();

/**
 * S-6. 본인 출석 현황. 학부모 화면(P-2)과 <b>같은 데이터·같은 구성</b>이다 —
 * 수업 출석 캘린더 위에 클리닉 점을 얹고, 아래에 그 달 클리닉의 출결을 나열한다.
 * 두 화면이 어긋나면 "엄마 폰에는 다르게 나온다"는 문의가 된다.
 */
export default function StudentAttendancePage() {
  const [year, setYear] = useState(NOW.getFullYear());
  const [month, setMonth] = useState(NOW.getMonth() + 1);

  const from = `${year}-${String(month).padStart(2, "0")}-01`;
  const to = `${year}-${String(month).padStart(2, "0")}-${String(
    new Date(year, month, 0).getDate(),
  ).padStart(2, "0")}`;

  const { data, isPending } = useQuery({
    queryKey: ["student", "attendances", year, month],
    queryFn: () => getMyAttendances(year, month),
  });

  const clinics = useQuery({
    queryKey: ["student", "clinics", from, to],
    queryFn: () => listMyClinics(from, to),
  });

  /*
    신청 가능한 시간대까지 다 내려오는 응답이라 내 예약만 골라낸다.
    myReservation이 null인 클리닉을 남기면 남이 신청한 시간대가 내 출결로 보인다.
  */
  const myClinics = useMemo(
    () => (clinics.data ?? []).filter((clinic) => clinic.myReservation !== null),
    [clinics.data],
  );

  // 캘린더는 수업과 같은 칩으로 그린다. attendStatus가 null이면 아직 확정 전이라 PENDING이다
  const clinicEntries = useMemo(
    () =>
      myClinics.map((clinic) => ({
        date: clinic.clinicDate,
        status: clinic.myReservation?.attendStatus ?? ("PENDING" as const),
      })),
    [myClinics],
  );

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
        <AttendanceCalendar
          data={data}
          onPrev={() => shift(-1)}
          onNext={() => shift(1)}
          clinics={clinicEntries}
        />
      )}

      <section>
        <h3 className="text-sm font-semibold text-slate-700">클리닉 출결</h3>
        {myClinics.length > 0 ? (
          <ul className="mt-2 space-y-2">
            {myClinics.map((clinic) => (
              <li
                key={clinic.clinicId}
                className="flex items-center justify-between gap-2 rounded-xl bg-white p-3
                           shadow-sm"
              >
                <p className="text-sm font-medium text-slate-900">
                  {clinic.clinicDate.slice(5)} (
                  {DAY_LABELS[new Date(clinic.clinicDate).getDay() || 7]}) {clinic.startTime}~
                  {clinic.endTime}
                </p>
                {/* attendStatus가 null이면 결석이 아니라 아직 출석 확정 전이다 */}
                <span
                  className={`rounded-md px-2 py-1 text-xs ${
                    clinic.myReservation?.attendStatus
                      ? DAY_STATUS_STYLE[clinic.myReservation.attendStatus].cell
                      : DAY_STATUS_STYLE.PENDING.cell
                  }`}
                >
                  {clinic.myReservation?.attendStatus
                    ? DAY_STATUS_STYLE[clinic.myReservation.attendStatus].label
                    : "미확인"}
                </span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-2 rounded-xl bg-white p-4 text-sm text-slate-500 shadow-sm">
            이 달에 신청한 클리닉이 없습니다.
          </p>
        )}
      </section>

      <p className="text-xs text-slate-500">
        회색 "미확인"은 선생님이 아직 출석을 확정하지 않은 날입니다. 결석이 아닙니다.
      </p>
    </div>
  );
}
