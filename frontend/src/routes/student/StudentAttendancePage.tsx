import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { AttendanceCalendar } from "../../shared/components/AttendanceCalendar";
import { PageTitle } from "../../shared/components/Section";
import { getMyAttendances, listMyClinics } from "./api";

const NOW = new Date();

/**
 * S-6. 본인 출석 현황. 학부모 화면(P-2)과 <b>같은 데이터·같은 구성</b>이다 —
 * 캘린더 칸에 수업과 클리닉을 같은 칩으로 넣는다.
 * 두 화면이 어긋나면 "엄마 폰에는 다르게 나온다"는 문의가 된다.
 *
 * <p>클리닉 목록을 아래에 따로 그리지 마라. 캘린더가 날짜와 출결을 이미 칩으로 보여준다 —
 * 같은 것을 줄줄이 늘어놓으면 화면만 길어진다.
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
      <PageTitle>출석 현황</PageTitle>
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

      <p className="text-xs text-slate-500">
        회색 칩은 선생님이 아직 출석을 확정하지 않은 날입니다. 결석이 아닙니다.
      </p>
    </div>
  );
}
