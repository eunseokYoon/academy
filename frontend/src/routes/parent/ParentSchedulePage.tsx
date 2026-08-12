import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSelectedChild } from "../../shared/auth/SelectedChildContext";
import { AttendanceCalendar } from "../../shared/components/AttendanceCalendar";
import { PageTitle } from "../../shared/components/Section";
import { ChildSelect } from "./ChildSelect";
import { getChildAttendances, getChildClinics } from "./api";

const NOW = new Date();

/**
 * P-2. 수업 출석과 클리닉을 한 화면에서 본다. <b>조회만</b>이다 —
 * 신청·취소·변경 버튼을 두지 마라.
 *
 * <p>자녀가 클리닉을 바꾸면 <b>공지가 한 건 발행된다</b>(수업일 변경과 같은 경로).
 * 여기에 변경 이력을 따로 그리지 마라 — 공지 탭과 같은 내용이 두 곳에 남는다.
 *
 * <p>클리닉 목록도 따로 그리지 마라. 캘린더가 그 달의 클리닉을 날짜·출석 상태까지 이미
 * 칩으로 보여준다 — 아래에 같은 것을 줄줄이 늘어놓으면 화면만 길어진다.
 * <b>다만 캘린더 칩에는 도착 시각이 없다</b>(칸이 40px이라 안 들어간다).
 * 몇 시에 가는지는 홈의 "다음 클리닉" 카드가 답한다.
 */
export default function ParentSchedulePage() {
  const { selectedStudentId } = useSelectedChild();
  const [year, setYear] = useState(NOW.getFullYear());
  const [month, setMonth] = useState(NOW.getMonth() + 1);

  const attendances = useQuery({
    queryKey: ["parent", "attendances", selectedStudentId, year, month],
    queryFn: () => getChildAttendances(selectedStudentId!, year, month),
    enabled: selectedStudentId !== null,
  });

  const clinics = useQuery({
    queryKey: ["parent", "clinics", selectedStudentId, year, month, null],
    queryFn: () => getChildClinics(selectedStudentId!, year, month),
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
      <PageTitle action={<ChildSelect />}>수업 · 클리닉 일정</PageTitle>

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

      {/* 칩에서 "미확인" 글자를 뺐으니 회색이 무슨 뜻인지는 여기서 말해야 한다 */}
      <p className="text-xs text-slate-500">
        회색 칩은 선생님이 아직 출석을 확정하지 않은 날입니다. 결석이 아닙니다.
      </p>
    </div>
  );
}
