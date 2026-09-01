import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { errorMessage } from "../../../shared/api/errors";
import { Badge } from "../../../shared/components/Badge";
import {
  confirmAttendance,
  confirmClinicAttendance,
  getAttendanceRoster,
  listClinicReservations,
  listPendingAttendance,
  listWeekAttendance,
} from "../api";
import type { AttendanceException } from "../api";
import { currentWeekOfMonth, dayLabel } from "../../../shared/date";
import { RosterEditor } from "./RosterEditor";
import { CLINIC_EXCEPTION_STATUSES } from "../../../shared/attendance/types";

/**
 * T-5. 미확정 수업·클리닉을 먼저 보여주고, 하나를 고르면 그 자리에서 출석을 확정한다.
 *
 * <p>선생님이 매일 여는 화면이라 "무엇이 남았는지"가 목록의 첫 줄이어야 한다.
 * 반·날짜를 매번 고르게 하면 확정을 빠뜨린다.
 *
 * <p>클리닉을 여기 같이 두는 이유도 같다. 클리닉 관리 화면에 들어가야만 확정할 수 있으면
 * 확정 안 된 클리닉이 조용히 쌓인다. 확정하는 곳은 두 화면이지만 <b>남은 것을 보는 곳은
 * 여기 하나</b>여야 한다.
 */
export default function AttendancePage() {
  /*
    T-1에서 "출석 확정하기"를 누르면 그 수업이 바로 열려야 한다. 목록으로 한 번 더
    들여보내면 선생님이 방금 본 수업을 다시 찾아야 한다.

    쿼리 파라미터는 첫 렌더에서 한 번만 읽는다. 뒤로 가기로 목록에 돌아갈 때
    파라미터를 지우므로, 계속 읽으면 목록이 다시 상세로 튕긴다.
  */
  const [searchParams, setSearchParams] = useSearchParams();
  // 수업과 클리닉은 확정 API가 달라서 선택 상태도 따로 둔다
  const [lessonId, setLessonId] = useState<number | null>(() => {
    const value = searchParams.get("lessonId");
    return value === null ? null : Number(value);
  });
  const [clinicId, setClinicId] = useState<number | null>(() => {
    const value = searchParams.get("clinicId");
    return value === null ? null : Number(value);
  });

  /** 목록으로 돌아갈 때 파라미터를 지운다. 안 지우면 새로고침에서 다시 상세가 열린다. */
  function backToList() {
    setLessonId(null);
    setClinicId(null);
    if (searchParams.has("lessonId") || searchParams.has("clinicId")) {
      setSearchParams({}, { replace: true });
    }
  }

  const pending = useQuery({
    queryKey: ["teacher", "attendance", "pending"],
    queryFn: listPendingAttendance,
  });

  if (lessonId !== null) {
    return <LessonConfirmPanel lessonId={lessonId} onBack={backToList} />;
  }
  if (clinicId !== null) {
    return <ClinicConfirmPanel clinicId={clinicId} onBack={backToList} />;
  }

  const lessons = pending.data?.lessons ?? [];
  const clinics = pending.data?.clinics ?? [];

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">출석 확정</h2>
        <p className="mt-1 text-sm text-slate-500">
          지난 수업·클리닉 중 아직 확정하지 않은 날입니다. 미래 일정은 여기 뜨지 않습니다.
        </p>
      </div>

      {pending.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : lessons.length === 0 && clinics.length === 0 ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          확정할 수업·클리닉이 없습니다.
        </p>
      ) : (
        <>
          <section>
            <h3 className="text-sm font-semibold text-slate-700">수업</h3>
            {lessons.length === 0 ? (
              <p className="mt-2 rounded-xl bg-white p-4 text-sm text-slate-500 shadow-sm">
                확정할 수업이 없습니다.
              </p>
            ) : (
              <ul className="mt-2 space-y-2">
                {lessons.map((lesson) => (
                  <li key={lesson.lessonId}>
                    <button
                      type="button"
                      onClick={() => setLessonId(lesson.lessonId)}
                      className="block w-full rounded-xl bg-white p-3 text-left shadow-sm"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium text-slate-900">
                          {lesson.lessonDate} ({dayLabel(lesson.lessonDate)})
                        </span>
                        <Badge tone="warn">미확정</Badge>
                      </div>
                      <p className="mt-0.5 text-sm text-slate-500">
                        {lesson.classRoomName} · {lesson.studentCount}명
                      </p>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section>
            <h3 className="text-sm font-semibold text-slate-700">클리닉</h3>
            {clinics.length === 0 ? (
              <p className="mt-2 rounded-xl bg-white p-4 text-sm text-slate-500 shadow-sm">
                확정할 클리닉이 없습니다.
              </p>
            ) : (
              <ul className="mt-2 space-y-2">
                {clinics.map((clinic) => (
                  <li key={clinic.clinicId}>
                    <button
                      type="button"
                      onClick={() => setClinicId(clinic.clinicId)}
                      className="block w-full rounded-xl bg-white p-3 text-left shadow-sm"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium text-slate-900">
                          {clinic.clinicDate} ({dayLabel(clinic.clinicDate)})
                        </span>
                        <Badge tone="warn">미확정</Badge>
                      </div>
                      {/* 클리닉엔 반 이름이 없다. 시각이 그 시간대를 가리키는 유일한 표시다 */}
                      <p className="mt-0.5 text-sm text-slate-500">
                        {clinic.startTime}~{clinic.endTime} · {clinic.studentCount}명
                      </p>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}

      <WeekSection onLesson={setLessonId} onClinic={setClinicId} />
    </div>
  );
}

/**
 * 지난 출석. <b>확정된 것도 보여준다</b>(2026-09-01 확정) — 고치려면 들어갈 입구가 있어야 한다.
 *
 * <p>위의 미확정 목록을 이걸로 대체하지 마라. 선생님이 매일 여는 화면이라 "무엇이
 * 남았는지"가 첫 줄이어야 하고, 주차를 골라야만 보이면 확정을 빠뜨린다.
 *
 * <p>누르면 미확정과 같은 확정 화면으로 간다. 이미 확정된 건이면 RosterEditor가 저장된
 * 상태를 채우고 버튼이 「수정 저장」으로 바뀐다 — 수정용 화면을 따로 만들지 마라.
 */
function WeekSection({
  onLesson,
  onClinic,
}: {
  onLesson: (id: number) => void;
  onClinic: (id: number) => void;
}) {
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [week, setWeek] = useState(currentWeekOfMonth(now));

  const data = useQuery({
    queryKey: ["teacher", "attendance", "week", year, month, week],
    queryFn: () => listWeekAttendance(year, month, week),
  });

  const lessons = data.data?.lessons ?? [];
  const clinics = data.data?.clinics ?? [];
  const empty = lessons.length === 0 && clinics.length === 0;

  return (
    <section className="space-y-3 border-t border-slate-200 pt-5">
      <div>
        <h3 className="text-sm font-semibold text-slate-700">지난 출석</h3>
        <p className="mt-1 text-xs text-slate-500">
          확정한 출석도 눌러서 고칠 수 있습니다. 아직 오지 않은 날은 나오지 않습니다.
        </p>
      </div>

      <div className="flex gap-2">
        <select
          value={year}
          onChange={(e) => setYear(Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2 text-sm"
        >
          {[now.getFullYear() - 1, now.getFullYear()].map((y) => (
            <option key={y} value={y}>
              {y}년
            </option>
          ))}
        </select>
        <select
          value={month}
          onChange={(e) => setMonth(Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2 text-sm"
        >
          {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
            <option key={m} value={m}>
              {m}월
            </option>
          ))}
        </select>
        <select
          value={week}
          onChange={(e) => setWeek(Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2 text-sm"
        >
          {[1, 2, 3, 4, 5].map((w) => (
            <option key={w} value={w}>
              {w}주차
            </option>
          ))}
        </select>
      </div>

      {data.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : empty ? (
        <p className="rounded-xl bg-white p-4 text-center text-sm text-slate-500 shadow-sm">
          그 주에는 지난 수업·클리닉이 없습니다.
        </p>
      ) : (
        <ul className="space-y-2">
          {lessons.map((lesson) => (
            <li key={`lesson-${lesson.lessonId}`}>
              <button
                type="button"
                onClick={() => onLesson(lesson.lessonId)}
                className="block w-full rounded-xl bg-white p-3 text-left shadow-sm"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-medium text-slate-900">
                    {lesson.lessonDate} ({dayLabel(lesson.lessonDate)})
                  </span>
                  {lesson.confirmed ? (
                    <Badge tone="ok">확정</Badge>
                  ) : (
                    <Badge tone="warn">미확정</Badge>
                  )}
                </div>
                <p className="mt-0.5 text-sm text-slate-500">
                  {lesson.classRoomName} · {lesson.studentCount}명
                </p>
              </button>
            </li>
          ))}
          {clinics.map((clinic) => (
            <li key={`clinic-${clinic.clinicId}`}>
              <button
                type="button"
                onClick={() => onClinic(clinic.clinicId)}
                className="block w-full rounded-xl bg-white p-3 text-left shadow-sm"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-medium text-slate-900">
                    {clinic.clinicDate} ({dayLabel(clinic.clinicDate)})
                  </span>
                  {/*
                    분모는 학생이 배정된 슬롯 수다. 전부 확정이면 「확정」, 일부면 「2/5 확정」,
                    아무도 없으면 확정할 것이 없어 배지를 안 그린다
                  */}
                  {clinic.studentSlotCount === 0 ? null : clinic.attendanceConfirmed ? (
                    <Badge tone="ok">확정</Badge>
                  ) : (
                    <Badge tone="warn">
                      {clinic.confirmedSlotCount}/{clinic.studentSlotCount} 확정
                    </Badge>
                  )}
                </div>
                <p className="mt-0.5 text-sm text-slate-500">
                  클리닉 {clinic.startTime}~{clinic.endTime} · {clinic.reservedCount}명
                </p>
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function BackButton({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="text-sm text-slate-500 underline">
      ‹ 목록으로
    </button>
  );
}

function LessonConfirmPanel({ lessonId, onBack }: { lessonId: number; onBack: () => void }) {
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  const roster = useQuery({
    queryKey: ["teacher", "attendance", "roster", lessonId],
    queryFn: () => getAttendanceRoster(lessonId),
  });

  const mutation = useMutation({
    mutationFn: (exceptions: AttendanceException[]) => confirmAttendance(lessonId, exceptions),
    onSuccess: async (result) => {
      setError(null);
      setDone(
        `확정했습니다. 출석 ${result.summary.present} · 지각 ${result.summary.late} · ` +
          `결석 ${result.summary.absent} · 병결 ${result.summary.sick} · ` +
          `공결 ${result.summary.excused} · 대체 등원 ${result.summary.makeup}`,
      );
      await queryClient.invalidateQueries({ queryKey: ["teacher", "attendance"] });
      await queryClient.invalidateQueries({ queryKey: ["teacher", "lessons"] });
    },
    onError: (e) => {
      setDone(null);
      setError(errorMessage(e, "출석을 확정하지 못했습니다."));
    },
  });

  return (
    <div className="space-y-4">
      <BackButton onClick={onBack} />

      {roster.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : roster.data ? (
        <>
          <div>
            <h2 className="text-lg font-semibold text-slate-900">{roster.data.classRoomName}</h2>
            <p className="mt-1 text-sm text-slate-500">
              {roster.data.lessonDate} · 안 온 학생만 눌러 상태를 바꾸세요.
            </p>
          </div>

          {done && (
            <p className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{done}</p>
          )}

          <RosterEditor
            rows={roster.data.students}
            confirmed={roster.data.attendanceStatus === "CONFIRMED"}
            pending={mutation.isPending}
            error={error}
            onConfirm={(exceptions) => mutation.mutate(exceptions)}
          />
        </>
      ) : (
        <p className="text-sm text-red-600">수업 정보를 불러오지 못했습니다.</p>
      )}
    </div>
  );
}

/**
 * 클리닉 확정. 명단 UI는 수업과 같은 RosterEditor지만 <b>쓰는 곳이 다르다</b> —
 * attendances가 아니라 clinic_reservations.attend_status에 기록된다.
 * 클리닉 출석을 attendances에 넣지 마라. 수업 출석률이 오염된다.
 *
 * <p>출석 확정은 <b>도착 시각 슬롯별로 따로</b> 낸다(2026-09-01 확정, T-13과 같은 규칙).
 * 이 화면에 뜬 클리닉도 일부 슬롯만 확정된 채로 "미확정"에 남을 수 있으므로 슬롯을 먼저
 * 고르게 한다. {@link RosterEditor}는 T-13(ClinicPage)의 슬롯 탭과 같은 방식으로 쓴다.
 */
function ClinicConfirmPanel({ clinicId, onBack }: { clinicId: number; onBack: () => void }) {
  const queryClient = useQueryClient();
  const [slot, setSlot] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  const reservations = useQuery({
    queryKey: ["teacher", "clinic-reservations", clinicId],
    queryFn: () => listClinicReservations(clinicId),
  });

  const slotStates = reservations.data?.slotStates ?? [];
  const activeSlot = slot ?? slotStates.find((s) => s.reservedCount > 0)?.arrivalTime ?? "";
  const current = slotStates.find((s) => s.arrivalTime === activeSlot);
  const rows = (reservations.data?.students ?? []).filter(
    (student) => student.arrivalTime === activeSlot,
  );

  const mutation = useMutation({
    mutationFn: (exceptions: AttendanceException[]) =>
      confirmClinicAttendance(clinicId, activeSlot, exceptions),
    onSuccess: async (result) => {
      setError(null);
      setDone(`확정했습니다. 출석 ${result.summary.present} · 결석 ${result.summary.absent}`);
      await queryClient.invalidateQueries({ queryKey: ["teacher", "attendance"] });
      await queryClient.invalidateQueries({ queryKey: ["teacher", "clinic-reservations"] });
      await queryClient.invalidateQueries({ queryKey: ["teacher", "clinics"] });
    },
    onError: (e) => {
      setDone(null);
      setError(errorMessage(e, "출석을 확정하지 못했습니다."));
    },
  });

  function selectSlot(arrivalTime: string) {
    setSlot(arrivalTime);
    setDone(null);
    setError(null);
  }

  return (
    <div className="space-y-4">
      <BackButton onClick={onBack} />

      {reservations.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : reservations.data ? (
        <>
          <div>
            <h2 className="text-lg font-semibold text-slate-900">
              클리닉 {reservations.data.startTime}~{reservations.data.endTime}
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              {reservations.data.clinicDate} · 도착 시각을 고르고, 안 온 학생만 눌러 상태를
              바꾸세요.
            </p>
          </div>

          {slotStates.every((s) => s.reservedCount === 0) ? (
            <p className="text-sm text-slate-400">배정된 학생이 없습니다.</p>
          ) : (
            <div className="flex flex-wrap gap-1">
              {slotStates.map((s) => (
                <button
                  key={s.arrivalTime}
                  type="button"
                  disabled={s.reservedCount === 0}
                  onClick={() => selectSlot(s.arrivalTime)}
                  aria-pressed={activeSlot === s.arrivalTime}
                  className={`rounded-lg px-2.5 py-1.5 text-sm disabled:cursor-not-allowed
                              disabled:opacity-40 ${
                    activeSlot === s.arrivalTime
                      ? "bg-slate-900 font-medium text-white"
                      : "border border-slate-300 text-slate-700"
                  }`}
                >
                  {s.arrivalTime} · {s.reservedCount === 0 ? "배정 없음" : `${s.reservedCount}명`}
                  {s.confirmed ? " ✓" : ""}
                </button>
              ))}
            </div>
          )}

          {done && (
            <p className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{done}</p>
          )}

          {activeSlot && (
            <RosterEditor
              rows={rows.map((student) => ({
                studentId: student.studentId,
                name: student.name,
                // attendStatus가 null이면 아직 확정 전이다. 기본값 출석으로 열어 준다
                status: student.attendStatus ?? "PRESENT",
                memo: student.memo,
              }))}
              confirmed={current?.confirmed ?? false}
              pending={mutation.isPending}
              error={error}
              onConfirm={(exceptions) => mutation.mutate(exceptions)}
              statuses={CLINIC_EXCEPTION_STATUSES}
            />
          )}
        </>
      ) : (
        <p className="text-sm text-red-600">클리닉 정보를 불러오지 못했습니다.</p>
      )}
    </div>
  );
}
